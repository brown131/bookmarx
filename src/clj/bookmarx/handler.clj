(ns bookmarx.handler
  (:require [compojure.core :refer [GET POST PUT DELETE defroutes]]
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

(defn sort-folder-children "Sort the children of a folder by a sort function."
  [folder sort-fn]
  (let [[l f] (map vec ((juxt filter remove) :bookmark/url (:bookmark/children folder)))]
    (update-in folder [:bookmark/children]
               #(into [] (concat (sort-by sort-fn f) (sort-by sort-fn l))))))

(defn get-bookmarks
  "Get all bookmark folders and their children and return them in an HTTP response."
  [params & [status]]
  (try
    (info "get-bookmarks")
    (let [keys (map read-string (second (wcar* (car/select 1)
                                               (car/keys "*"))))
          values (wcar* (apply car/mget keys))
          bookmarks (zipmap keys values)
          headers {"content-type" "application/edn"}]
      {:status (or status 200)
       :headers (if (= (:csrf-token params) "true")
                  (assoc headers "csrf-token" *anti-forgery-token*)
                  headers)
       :body (pr-str bookmarks)})
    (catch Exception e (errorf "Error %s" (.toString e)))))

(defn get-bookmark "Get a bookmark in an HTTP response."
  [id params & [status]]
  (try
    (infof "get-bookmark %s %s" id params)
    (let [bookmark (wcar* (car/select 1)
                          (car/get (:id params)))
          headers {"content-type" "application/edn"}]
      {:status (or status 200)
       :headers (if (= (:csrf-token params) "true")
                  (assoc headers "csrf-token" *anti-forgery-token*)
                  headers)
       :body (pr-str (first bookmark))})
  (catch Exception e (errorf "Error %s" (.toString e)))))

(defn post-bookmark "Add a bookmark into the database for an HTTP request."
  [params & [status]]
  (try
    (infof "post-bookmark %s" params)
    (let [id (wcar* (car/select 1)
                    (car/incr "last-bookmark-id"))
          now (java.util.Date.)
          bookmark (assoc params :bookmark/id id :bookmark/created now :bookmark/last-visited now
                          :bookmark/visits 1)
          parent (wcar* (car/get (:bookmark/parent bookmark)))]
      (infof "bookmark %s" (str bookmark))
      (wcar*
        (car/set (:bookmark/parent bookmark)
                 (update-in parent [:bookmark/children] #(conj % bookmark)))
        (car/bgsave))
      {:status (or status 200)
       :headers {"content-type" "application/edn"}
       :body (pr-str bookmark)})
  (catch Exception e (errorf "Error %s" (.toString e)))))

(defn put-bookmark "Update a bookmark in the database for an HTTP request."
  [id params & [status]]
  (try
    (infof "put-bookmark %s %s" id params)
    (let [now (java.util.Date.)
          bookmark (update-in params [:bookmark/last-visited] now)
          parent (wcar* (car/select 1)
                        (car/get (:bookmark/parent bookmark)))]
      (infof "bookmark %s" (str bookmark))
      (wcar*
        (car/set (:bookmark/parent bookmark)
                 (update-in parent [:bookmark/children]
                            #(mapv (fn [c] (if (= :bookmark/id id) bookmark c)) %)))
        (car/bgsave))
      {:status (or status 200)
       :headers {"content-type" "application/edn"}
       :body (pr-str bookmark)})
    (catch Exception e (errorf "Error %s" (.toString e)))))

(defn delete-bookmark "Retract a bookmark in the database."
  [id & [status]]
  (try
    (infof "delete-bookmark %s" id)
    (wcar*
      (car/select 1)
      (car/del id)
      (car/bgsave))

      ;; TODO: If the bookmark is a folder, recursively delete its children,
      ;; moving links to the trash folder.
      
      {:status (or status 200)}
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
