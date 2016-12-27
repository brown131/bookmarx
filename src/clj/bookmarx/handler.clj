(ns bookmarx.handler
  (:require [clojure.data :refer [diff]]
            [clojure.string :as str]
            [compojure.core :refer [GET POST PUT DELETE defroutes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [include-js include-css html5]]
            [bookmarx.middleware :refer [wrap-middleware]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]
            [taoensso.carmine :as car]
            [ring.middleware.anti-forgery :refer :all]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.edn :refer :all])
  (:gen-class))

(timbre/refer-timbre)

;; Setup redis connection.
(defonce bookmarx-conn {:pool {} :spec {}})
(defmacro wcar* [& body] `(car/wcar bookmarx-conn (car/select 1) ~@body))

;; Cache
(def bookmarks (atom (let [keys (remove symbol? (map read-string (second (wcar* (car/keys "*")))))
                           values (second (wcar* (apply car/mget keys)))]
                       (zipmap keys values))))

(defn head []
  [:head
   [:title "Bookmarx"]
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:link {:rel "icon" :type "image/png" :href "favicon.ico"}]
   (include-css (if (env :dev) "css/site.css" "css/site.min.css"))
   (include-css "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css")])

(def loading-page
  (html5
    (head)
    [:body {:class "body-container"}
     [:div#app]
     [:script {:type "text/javascript"} "var env='" (pr-str (env :client-env)) "';"]
     (include-js "js/app.js")
     (include-js "https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js")
     (include-js "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js")]))

(def cards-page
  (html5
    (head)
    [:body
     [:div#app]
     [:script {:type "text/javascript"} "var env='" (pr-str (env :client-env)) "';"]
     (include-js "js/app_devcards.js")
     (include-js "https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js")
     (include-js "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js")]))

(defn get-headers [params]
  (let [headers {"content-type" "application/edn"}]
    (if (= (:csrf-token params) "true")
      (assoc headers "csrf-token" *anti-forgery-token*)
      headers)))

(defn sort-folder-children "Sort the children of a folder by a sort function."
  [folder]
  (let [[links folders] (map vec ((juxt filter remove) :bookmark/url (:bookmark/children folder)))]
    (update-in folder [:bookmark/children]
               (fn [_] (into [] (concat (sort-by #(str/upper-case (:bookmark/title (get @bookmarks %)))
                                                 folders)
                                        (sort-by #(str/upper-case (:bookmark/title %)) links)))))))

(defn get-response "Save changed bookmarks and build the response."
  [changed-ids]
  ; Update the revision.
  (let [latest-revision (second (wcar* (car/incr "latest-revision")))]
    ;; Set the revision in the changed bookmarks.
    (dorun (map #(swap! bookmarks update-in [% :bookmark/revision] (constantly latest-revision))
                changed-ids))

    ;; Save the changes.
    (wcar*
      (dorun (map #(car/set % (get @bookmarks %)) changed-ids))
      (car/bgsave))

    ;; Return the list of changed folders and the latest revision.
    {:bookmarks (mapv #(get @bookmarks %) changed-ids) :revision latest-revision}))

(defn get-bookmarks
  "Get all bookmark folders and their children and return them in an HTTP response."
  [params & [status]]
  (try
    (info "get-bookmarks")
    (let [revision (Integer/parseInt (second (wcar* (car/get "latest-revision"))))]
      {:status (or status 200)
       :headers (get-headers params)
       :body (pr-str {:bookmarks (into [] (vals (dissoc @bookmarks :latest-revision)))
                      :revision revision})})
    (catch Exception e (errorf "Error %s" (.toString e)))))

(defn get-revised-bookmarks "Get bookmarks greater than a revision number in an HTTP request."
  [rev params & [status]]
  (try
    (infof "get-revised-bookmarks %s %s" rev params)
    (let [revision (Integer/parseInt rev)]
      {:status (or status 200)
       :headers (get-headers params)
       :body (pr-str {:bookmarks (into [] (filter #(> (:bookmark/revision %) revision) @bookmarks))
                      :revision (second (wcar* (car/get "latest-revision")))})})
      (catch Exception e (errorf "Error %s" (.toString e)))))

(defn post-bookmark "Add a bookmark into the database for an HTTP request."
  [params & [status]]
  (try
    (infof "post-bookmark %s" params)
    (let [bookmark-id (second (wcar* (car/incr "last-bookmark-id")))
          now (java.util.Date.)
          bookmark (assoc params :bookmark/id bookmark-id :bookmark/created now
                                 :bookmark/last-visited now :bookmark/visits 1
                                 :bookmark/revision 1)
          is-link? (:bookmark/url bookmark)
          parent-id (:bookmark/parent-id bookmark)
          changed-ids
          (loop [ancestor-ids [parent-id]]
            (let [ancestor (get @bookmarks (first ancestor-ids))]
              (if (not (and is-link? (:bookmark/parent-id ancestor)))
                (reverse ancestor-ids)
                (recur (cons (:bookmark/parent-id ancestor) ancestor-ids)))))]
      ;; Increment the link count in the ancestors.
      (when is-link?
        (dorun (map #(swap! bookmarks update-in [% :bookmark/link-count] inc) changed-ids)))

      ;; Add the new folder to cache.
      (when-not is-link?
        (swap! bookmarks assoc bookmark-id bookmark))

      ;; Add the bookmark to its parent folder.
      (swap! bookmarks update-in [parent-id :bookmark/children]
             #(vec (cons (if is-link? bookmark bookmark-id) %)))

      ;; Re-sort the bookmarks in the parent folder.
      (swap! bookmarks update parent-id sort-folder-children)

      {:status (or status 200)
       :headers {"content-type" "application/edn"}
       :body (pr-str (assoc (get-response changed-ids) :bookmark-id bookmark-id))})
    (catch Exception e (errorf "Error %s" (.toString e)))))

(defn update-bookmark "Update a bookmark."
  [{:keys [:bookmark/id :bookmark/parent-id :bookmark/url] :as bookmark}]
  ;; If it's a link, update it in it's parent.
  (if url
    ;; TODO: Update only relevant fields.
    (swap! bookmarks update-in [parent-id :bookmark/children]
     #(mapv (fn [b] (if (and (:bookmark/url b) (= id (:bookmark/id b)))
                      (merge b (select-keys bookmark [:bookmark/title :boomark/url :bookmark/rating
                                                      :bookmark/icon :bookmark/icon-color])))) %))
    (swap! bookmarks #(merge % (select-keys bookmark [:bookmark/title]))))

  ;; Return the changed folder.
  [(get @bookmarks (if url parent-id id))])

(defn move-bookmark "Move a bookmark to a different folder."
  [{:keys [:bookmark/id :bookmark/parent-id :bookmark/url] :as bookmark}]
  (let [orig-parent-id (:bookmark/parent-id (get @bookmarks id))
        is-link? (:bookmark/url bookmark)
        ancestor-ids
        (loop [a-ids [parent-id]]
          (let [a-id (first a-ids)
                a (get @bookmarks a-id)]
            (if-not (:bookmark/parent-id a) a-ids (recur (cons (:bookmark/parent-id a) a-ids)))))
        orig-ancestor-ids
        (loop [oa-ids [parent-id]]
          (let [oa-id (first oa-ids)
                oa (get @bookmarks oa-id)]
            (if-not (:bookmark/parent-id oa) oa-ids (recur (cons (:bookmark/parent-id oa) oa-ids)))))
        diffs (diff ancestor-ids orig-ancestor-ids)
        changed-ids (concat (first diff) (second diff))]
    ;; Increment the link count in the ancestors.
    (dorun (map #(swap! bookmarks update-in [% :bookmark/link-count] inc) (first diffs)))

    ;; Decrement the link count in the original ancestors.
    (dorun (map #(swap! bookmarks update-in [% :bookmark/link-count] dec) (second diffs)))

    ;; Remove the bookmark from the original folder.
    (swap! bookmarks update-in [orig-parent-id :bookmark/children]
           #(remove (fn [b] (= (if (:bookmark/url b) (:bookmark/id b) b) id)) %))

    ;; Add the bookmark to the new folder.
    (swap! bookmarks update-in [parent-id :bookmark/children]
           #(conj % (if (:bookmark/url %) bookmark id)))

    ;; Resort the bookmarks in the folder if the title changed.
    (when-not (= (:bookmark/title bookmark)
                 (get-in @bookmarks [orig-parent-id :bookmark/title]))
      (sort-folder-children (get @bookmarks parent-id)))

    ;; Return the list of changed folders.
    (mapv #(get @bookmarks %) changed-ids)))

(defn put-bookmark "Update a bookmark in the database for an HTTP request."
  [id bookmark & [status]]
  (try
    (infof "put-bookmark %s %s" id bookmark)
    (let [parent-id (:bookmark/parent-id bookmark)
          orig-parent-id (get-in @bookmarks [(:bookmark/id bookmark) :bookmark/parent-id])
          changed-ids (if (= (:bookmark/parent-id bookmark) orig-parent-id)
                        (update-bookmark bookmark)
                        (move-bookmark bookmark))]
      ;; Resort the bookmarks in the folder if the title changed.
      (when-not (= (:bookmark/title bookmark)
                   (get-in @bookmarks [orig-parent-id :bookmark/title]))
        (sort-folder-children (get @bookmarks parent-id)))

      ;; Return the list of changed folders.

      {:status (or status 200)
       :headers {"content-type" "application/edn"}
       :body (pr-str (get-response changed-ids))})
    (catch Exception e (errorf "Error %s" (.toString e)))))

(defn delete-bookmark "Delete a bookmark in the database."
  [id & [status]]
  (try
    (infof "delete-bookmark %s" id)
    (let [bookmark-id (Integer/parseInt id)
          bookmark (get @bookmarks bookmark-id)
          parent-id (:bookmark/parent-id bookmark)
          is-link? (:bookmark/url bookmark)
          changed-ids
          (loop [ancestor-ids [parent-id]]
            (let [ancestor (get @bookmarks (first ancestor-ids))]
              (if (not (and is-link? (:bookmark/parent-id ancestor)))
                (reverse ancestor-ids)
                (recur (cons (:bookmark/parent-id ancestor) ancestor-ids)))))
          deleted-bookmark-ids
          (when-not is-link?
            ;; Create a list with the folder and any child folders.
            (reduce #(if (:bookmark/url %2) %1 (cons %2 %1)) [bookmark-id]
                    (:bookmark/children bookmark)))]
      ;; Subtract the link count from the ancestor.
      (dorun (map #(swap! bookmarks update-in [% :bookmark/link-count]
                          (fn [b] (if (:bookmark/link-count bookmark)
                                    (- b (:bookmark/link-count bookmark))
                                    (dec b)))) changed-ids))

      ;; Increment the revision of the ancestor.
      (dorun (map #(swap! bookmarks update-in [% :bookmark/revision] inc) changed-ids))

      ;; Remove the bookmark from the parent.
      (swap! bookmarks update-in [parent-id :bookmark/children]
             #(remove (fn [b] (= (if (:bookmark/id b) (:bookmark/id b) b) bookmark-id)) %))

      ;; Remove bookmark(s) from the cache, if it is a folder.
      (when-not is-link?
        (dorun (map #(swap! bookmarks dissoc %) deleted-bookmark-ids)))

      ;; Delete the bookmark and any children, and update the parent.
      (wcar*
        (when-not is-link?
          (dorun (map car/del deleted-bookmark-ids)))
        (dorun (map #(car/set % (get @bookmarks %)) changed-ids))
        (car/bgsave))

      ;; Return the list of changed folders.
      {:status (or status 200)
       :headers {"content-type" "application/edn"}
       :body (pr-str (mapv #(get @bookmarks %) changed-ids))})
    (catch Exception e (errorf "Error %s" (.toString e)))))

(defroutes routes
  ;; Views
  (GET "/" [] loading-page)
  (GET "/add" [] loading-page)
  (GET "/about" [] loading-page)
  (GET "/cards" [] cards-page)
  (GET "/folder" [] loading-page)
  (GET "/icon" [] loading-page)
  (GET "/search" [] loading-page)

  ;; API
  (GET "/api/bookmarks/since/:rev" {rev :rev params :params} [] (get-revised-bookmarks rev params))
  (GET "/api/bookmarks" {params :params} [] (get-bookmarks params))
  (POST "/api/bookmarks" {params :edn-params} (post-bookmark params))
  (PUT "/api/bookmarks/:id" {id :id params :edn-params} [] (put-bookmark id params))
  (DELETE "/api/bookmarks/:id" [id] (delete-bookmark id))

  (resources "/")
  (not-found "Not Found"))

(def app
  (-> #'routes
      wrap-middleware
      wrap-edn-params
      (wrap-cors :access-control-allow-origin [#"https://www.browncross.com"
                                               #"http://localhost:3000"
                                               #"http://localhost:3449"]
                 :access-control-allow-methods [:get :post :put :delete])))
