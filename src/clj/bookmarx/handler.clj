(ns bookmarx.handler
  (:require [clojure.string :as str]
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
(defmacro wcar* [& body] `(car/wcar bookmarx-conn ~@body))

;; Cache
(def bookmarks (atom (let [keys (map read-string (second (wcar* (car/select 1)
                                                                (car/keys "*"))))
                           values (wcar* (apply car/mget keys))]
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
               (fn [_] (into [] (concat (sort-by #(str/upper-case (:bookmark/title (get @bookmarks (:bookmark/id %))))
                                                 folders)
                                        (sort-by #(str/upper-case (:bookmark/title %)) links)))))))

(defn get-bookmarks
  "Get all bookmark folders and their children and return them in an HTTP response."
  [params & [status]]
  (try
    (info "get-bookmarks")
    {:status (or status 200)
     :headers (get-headers params)
     :body (pr-str @bookmarks)}
    (catch Exception e (errorf "Error %s" (.toString e)))))

(defn get-bookmark "Get a bookmark in an HTTP response."
  [id params & [status]]
  (try
    (infof "get-bookmark %s %s" id params)
    {:status (or status 200)
     :headers (get-headers params)
     :body (pr-str (get @bookmarks id))}
    (catch Exception e (errorf "Error %s" (.toString e)))))

(defn post-bookmark "Add a bookmark into the database for an HTTP request."
  [params & [status]]
  (try
    (infof "post-bookmark %s" params)
    (let [bookmark-id (second (wcar* (car/select 1)
                                     (car/incr "last-bookmark-id")))
          now (java.util.Date.)
          bookmark (assoc params :bookmark/id bookmark-id :bookmark/created now
                                 :bookmark/last-visited now :bookmark/visits 1
                                 :bookmark/revision 1)
          is-link? (:bookmark/url bookmark)
          parent-id (:bookmark/parent bookmark)
          changed-bookmark-ids
          (loop [ancestor-ids [parent-id]]
            (let [ancestor-id (first ancestor-ids)
                  ancestor (get @bookmarks ancestor-id)]
              ;; Increment the link count in the ancestor if it is a link.
              (when is-link?
                (swap! bookmarks update-in [ancestor-id :bookmark/link-count] inc) )
              ;; Increment the revision of the ancestor.
              (swap! bookmarks update-in [ancestor-id :bookmark/revision] inc)
              (if (not (and is-link? (:bookmark/parent ancestor)))
                ancestor-ids
                (recur (cons (:bookmark/parent ancestor) ancestor-ids)))))]
      ;; Add the new folder to cache.
      (when-not is-link?
        (swap! bookmarks assoc bookmark-id bookmark))

      ;; Add the bookmark to its parent folder.
      (swap! bookmarks update-in [parent-id :bookmark/children]
             #(conj % (if is-link? bookmark {:bookmark/id bookmark-id})))

      ;; Re-sort the bookmarks in the parent folder.
      (swap! bookmarks update parent-id sort-folder-children)

      ;; Save the changes.
      (wcar*
        (dorun (map #(car/set % (get @bookmarks %)) changed-bookmark-ids))
        (car/bgsave))

      ;; Return the list of changed folders.
      {:status (or status 200)
       :headers {"content-type" "application/edn"}
       :body (pr-str (mapv #(get @bookmarks %) changed-bookmark-ids))})
    (catch Exception e (errorf "Error %s" (.toString e)))))

;; TODO: Rewrite this
(defn put-bookmark "Update a bookmark in the database for an HTTP request."
  [id params & [status]]
  (try
    (infof "put-bookmark %s %s" id params)
    (let [bookmark (:body params)
          bookmark-id (:bookmark/id bookmark)
          parent-id (:bookmark/parent bookmark)
          orig-parent-id (get-in @bookmarks [bookmark-id :bookmark/parent])
          bookmark (update-in params [:bookmark/last-visited] (java.util.Date.))
          changed-bookmarks (into [] (distinct []))]
      (if (= parent-id orig-parent-id)
        (if (:bookmark/url bookmark)
          ;; Update link in its folder.
          (swap! bookmarks update-in [parent-id :bookmark/children]
                 #(map (fn [b] (if (= (:bookmark/id b) bookmark-id) bookmark b)) %))
          ;; Update the folder.
          (swap! bookmarks update bookmark-id bookmark))
        (do
          ;; Remove bookmark from the original folder.
          (swap! bookmarks update-in [orig-parent-id :bookmark/children]
                 #(remove (fn [b] (= (:bookmark/id b) bookmark-id)) %))
          ;; Add the bookmark to the new folder.
          (swap! bookmarks update-in [parent-id :bookmark/children]
                 #(conj % (if (:bookmark/url %) bookmark {:bookmark/id bookmark-id})))

          ;; If it's a folder: update the folder.
          (when-not (:bookmark/url bookmark)
            (swap! bookmarks update bookmark-id bookmark))))

      ;; Resort the bookmarks in the folder if the title changed.
      (when-not (= (:bookmark/title bookmark)
                   (get-in @bookmarks [orig-parent-id :bookmark/title]))
        (sort-folder-children (get @bookmarks parent-id)))

      ;; Save the changed folders.
      (wcar*
        (map #(car/set (:bookmark/id %) %) changed-bookmarks)
        (car/bgsave))

      ;; Return the list of changed folders.
      {:status (or status 200)
       :headers {"content-type" "application/edn"}
       :body (pr-str changed-bookmarks)})
    (catch Exception e (errorf "Error %s" (.toString e)))))

(defn delete-bookmark "Delete a bookmark in the database."
  [id & [status]]
  (try
    (infof "delete-bookmark %s" id)
    (let [bookmark-id (Integer/parseInt id)
          bookmark (get @bookmarks bookmark-id)
          parent-id (:bookmark/parent bookmark)
          is-link? (:bookmark/url bookmark)
          changed-bookmark-ids
          (loop [ancestor-ids [parent-id]]
            (let [ancestor-id (first ancestor-ids)
                  ancestor (get @bookmarks ancestor-id)]
              ;; Subtract the link count from the ancestor.
              (swap! bookmarks update-in [ancestor-id :bookmark/link-count]
                     #(if (:bookmark/link-count bookmark)
                        (- % (:bookmark/link-count bookmark))
                        (dec %)))
              ;; Increment the revision of the ancestor.
              (swap! bookmarks update-in [ancestor-id :bookmark/revision] inc)
              (if (or is-link? (not (:bookmark/parent ancestor)))
                ancestor-ids
                (recur (conj (:bookmark/parent ancestor) ancestor-ids)))))
          deleted-bookmark-ids
          (when-not is-link?
             ;; Create a list with the folder and any child folders.
             (into [] (loop [bookmark-id (:bookmark/id bookmark)]
                        (let [children (remove :bookmark/url
                                               (:bookmark/children (get @bookmarks bookmark-id)))]
                          (cons bookmark-id (map #(recur (:bookmark/id %)) children))))))]
      ;; Remove the bookmark from the parent.
      (swap! bookmarks update-in [parent-id :bookmark/children]
             #(remove (fn [b] (= (:bookmark/id b) bookmark-id)) %))

      ;; Remove bookmark(s) from the cache, if it is a folder.
      (when-not is-link?
        (dorun (map #(swap! bookmarks dissoc %) deleted-bookmark-ids)))

      ;; Delete the bookmark and any children, and update the parent.
      (wcar*
        (car/select 1)
        (when-not is-link?
          (dorun (map car/del deleted-bookmark-ids)))
        (dorun (map #(car/set % (get @bookmarks %)) changed-bookmark-ids))
        (car/bgsave))

      ;; Return the list of changed folders.
      {:status (or status 200)
       :headers {"content-type" "application/edn"}
       :body (pr-str (mapv #(get @bookmarks %) changed-bookmark-ids))})
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
  (GET "/api/bookmarks" {params :params} [] (get-bookmarks params))
  (GET "/api/bookmarks/:id" {id :id params :params} [] (get-bookmark id params))
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
