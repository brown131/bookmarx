(ns bookmarx.handler
  (:require [clojure.set :refer [difference]]
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
  (let [[links folders] (map vec ((juxt filter remove) #(:bookmark/url (get @bookmarks %))
                                   (:bookmark/children folder)))
        make-sort-key #(if (:bookmark/title (get @bookmarks %))
                         (str/upper-case (:bookmark/title (get @bookmarks %)))
                         (do (println "NO TITLE" % (str (get @bookmarks %))) ""))]
    (update folder :bookmark/children
            (fn [_] (into [] (concat (if (empty? folders) folders (sort-by make-sort-key folders))
                                     (if (empty? links) links (sort-by make-sort-key links))))))))

(defn get-bookmarks
  "Get all bookmark folders and their children and return them in an HTTP response."
  [csrf-token]
  (try
    (info "get-bookmarks")
    {:status 200
     :headers {"content-type" "application/edn" "csrf-token" *anti-forgery-token*}
     :body (pr-str {:bookmarks (into [] (vals (remove #(keyword? (key %)) @bookmarks)))
                    :revision (Integer/parseInt (second (wcar* (car/get "latest-revision"))))})}
    (catch Exception e (errorf "Error %s" (.toString e)))))

(defn get-bookmarks-since "Get bookmarks greater than a revision number in an HTTP request."
  [rev csrf-token]
  (try
    (infof "get-revised-bookmarks %s" rev)
    (let [revision (Integer/parseInt rev)]
      {:status 200
       :headers {"content-type" "application/edn" "csrf-token" *anti-forgery-token*}
       :body (pr-str {:bookmarks (into [] (filter #(> (:bookmark/revision %) revision) @bookmarks))
                      :revision (Integer/parseInt (second (wcar* (car/get "latest-revision"))))})})
      (catch Exception e (errorf "Error %s" (.toString e)))))

(defn get-response "Save and build a response with the changed bookmarks."
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

    ;; Return the list of changed bookmarks and the latest revision.
    {:bookmarks (mapv #(get @bookmarks %) changed-ids) :revision latest-revision}))

(defn post-bookmark "Add a bookmark into the database for an HTTP request."
  [{:keys [:bookmark/url :bookmark/parent-id] :as bookmark}]
  (try
    (infof "post-bookmark %s" (pr-str bookmark))
    (let [bookmark-id (second (wcar* (car/incr "last-bookmark-id")))
          now (java.util.Date.)
          new-bookmark (assoc (if url bookmark (assoc bookmark :bookmark/children []
                                                               :bookmark/link-count 0))
                         :bookmark/id bookmark-id :bookmark/created now :bookmark/last-visited now
                         :bookmark/visits 1 :bookmark/revision 1)
          changed-ids
          (loop [ancestor-ids [parent-id bookmark-id]]
            (let [ancestor (get @bookmarks (first ancestor-ids))]
              (if-not (:bookmark/parent-id ancestor)
                (reverse ancestor-ids)
                (recur (cons (:bookmark/parent-id ancestor) ancestor-ids)))))]
      ;; Increment the link count in the ancestors.
      (when url
        (dorun (map #(swap! bookmarks update-in [% :bookmark/link-count] inc) (rest changed-ids))))

      ;; Add the new bookmark to cache.
      (swap! bookmarks assoc bookmark-id new-bookmark)

      ;; Add the bookmark to its parent folder.
      (swap! bookmarks update-in [parent-id :bookmark/children] #(conj % bookmark-id))

      ;; Re-sort the bookmarks in the parent.
      (swap! bookmarks update parent-id sort-folder-children)

      {:status 200
       :headers {"content-type" "application/edn"}
       :body (pr-str (get-response changed-ids))})
    (catch Exception e (errorf "Error %s" (.toString e)))))

(defn update-bookmark "Update a bookmark."
  [{:keys [:bookmark/id :bookmark/url] :as bookmark}]
  ;; Change the fields in the bookmark.
  (swap! bookmarks update id
         #(merge % (if url
                     (select-keys bookmark [:bookmark/title :boomark/url :bookmark/rating
                                            :bookmark/icon :bookmark/icon-color])
                     (select-keys bookmark [:bookmark/title]))))

  ;; Return a list with the id of the changed bookmark.
  [id])

(defn move-bookmark "Move a bookmark to a different folder."
  [{:keys [:bookmark/id :bookmark/parent-id :bookamrk/url] :as bookmark}]
  (let [orig-parent-id (:bookmark/parent-id (get @bookmarks id))
        ancestor-ids
        (loop [ancestor-ids [parent-id id]]
          (let [ancestor (get @bookmarks (first ancestor-ids))]
            (if-not (:bookmark/parent-id ancestor)
              (reverse ancestor-ids)
              (recur (cons (:bookmark/parent-id ancestor) ancestor-ids)))))
        orig-ancestor-ids
        (loop [ancestor-ids [orig-parent-id]]
          (let [ancestor (get @bookmarks (first ancestor-ids))]
            (if-not (:bookmark/parent-id ancestor)
              (reverse ancestor-ids)
              (recur (cons (:bookmark/parent-id ancestor) ancestor-ids)))))
        diffs1 (difference (set ancestor-ids) (set orig-ancestor-ids))
        diffs2 (difference (set orig-ancestor-ids) (set ancestor-ids))
        changed-ids (doall (concat diffs1 diffs2))]
    ;; Remove the bookmark from the original parent.
    (swap! bookmarks update-in [orig-parent-id :bookmark/children]
           #(into [] (remove (fn [b] (=  b id)) %)))

    ;; Add the bookmark to the new parent.
    (swap! bookmarks update-in [parent-id :bookmark/children] #(conj % id))

    ;; Change the fields in the bookmark.
    (swap! bookmarks update id
           #(merge % (if url
                       (select-keys bookmark [:bookmark/title :boomark/url :bookmark/rating
                                              :bookmark/icon :bookmark/icon-color])
                       (select-keys bookmark [:bookmark/title]))))

    ;; Add the link count to the ancestors.
    (dorun (map #(when (get-in @bookmarks [% :bookmark/link-count])
                   (swap! bookmarks update-in [% :bookmark/link-count]
                          (fn [b] (if (:bookmark/link-count bookmark)
                                    (+ b (:bookmark/link-count bookmark))
                                    (inc b))))) (into [] diffs1)))

    ;; Subtract the link count from the original ancestors.
    (dorun (map #(when (get-in @bookmarks [% :bookmark/link-count])
                   (swap! bookmarks update-in [% :bookmark/link-count]
                          (fn [b] (if (:bookmark/link-count bookmark)
                                    (- b (:bookmark/link-count bookmark))
                                    (dec b))))) (into [] diffs2)))

    ;; Return the list of changed bookmark ids.
    changed-ids))

(defn put-bookmark "Update a bookmark in the database for an HTTP request."
  [id bookmark]
  (try
    (infof "put-bookmark %s %s" id (pr-str bookmark))
    (let [orig-bookmark (get @bookmarks (:bookmark/id bookmark))
          changed-ids (if (= (:bookmark/parent-id bookmark) (:bookmark/parent-id orig-bookmark))
                        (update-bookmark bookmark)
                        (move-bookmark bookmark))]
      ;; Re-sort the bookmarks in the parent if the title changed.
      (when-not (= (:bookmark/title bookmark) (:bookmark/title orig-bookmark))
        (sort-folder-children (get @bookmarks (:bookmark/parent-id bookmark))))

      ;; Return the list of changed bookmarks.
      {:status 200
       :headers {"content-type" "application/edn"}
       :body (pr-str (get-response changed-ids))})
    (catch Exception e (errorf "Error %s" (.toString e)))))

(defn delete-bookmark "Delete a bookmark in the database."
  [id]
  (try
    (infof "delete-bookmark %s" id)
    (let [bookmark-id (Integer/parseInt id)
          {:keys [:bookmark/parent-id :bookmark/link-count]} (get @bookmarks bookmark-id)
          changed-ids
          (loop [ancestor-ids [parent-id]]
            (let [ancestor (get @bookmarks (first ancestor-ids))]
              (if-not (:bookmark/parent-id ancestor)
                (reverse ancestor-ids)
                (recur (cons (:bookmark/parent-id ancestor) ancestor-ids)))))
          deleted-ids
          ;; Create a list with the bookmark and its progeny.
          (loop [progeny-id bookmark-id]
            (let [children (get-in @bookmarks [progeny-id :bookmark/children])]
              (if-not children progeny-id
                (cons progeny-id (map #(recur %) children)))))]
      ;; Remove deleted bookmarks from the cache.
      ;(dorun (map #(swap! bookmarks (fn [b] (dissoc b %))) deleted-ids))
      (swap! bookmarks #(apply dissoc % deleted-ids))

      ;; Remove the bookmark from its parent.
      (swap! bookmarks update-in [parent-id :bookmark/children]
             #(into [] (remove (fn [b] (= b bookmark-id)) %)))

      ;; Subtract the link count from the ancestors.
      (dorun (map #(when (:bookmark/link-count %)
                     (swap! bookmarks update-in [% :bookmark/link-count]
                            (fn [b] (if link-count (- b link-count) (dec b))))) changed-ids))

      ;; Delete the bookmark and its progeny, and update its ancestors.
      (wcar*
        (dorun (map car/del deleted-ids))
        (dorun (map #(car/set % (get @bookmarks %)) changed-ids))
        (car/bgsave))

      ;; Return the list of changed folders.
      {:status 200
       :headers {"content-type" "application/edn"}
       :body (pr-str (assoc (get-response changed-ids) :deleted-ids (into [] deleted-ids)))})
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
  (GET "/api/bookmarks/since/:rev" {{rev :rev} :route-params {csrf-token :csrf-token} :params}
    [] (get-bookmarks-since rev csrf-token))
  (GET "/api/bookmarks" {{csrf-token :csrf-token} :params} [] (get-bookmarks csrf-token))
  (POST "/api/bookmarks" {bookmark :edn-params} (post-bookmark bookmark))
  (PUT "/api/bookmarks/:id" {{id :id} :route-params bookmark :edn-params} [] (put-bookmark id bookmark))
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
