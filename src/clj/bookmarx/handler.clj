(ns bookmarx.handler
  (:require [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [include-js include-css html5]]
            [bookmarx.middleware :refer [wrap-middleware]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]
            [datomic.api :as d]
            [ring.middleware.anti-forgery :refer :all]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.edn :refer :all])
  (:gen-class))

(timbre/refer-timbre)

(defn head []
  [:head
   [:title "Bookmarx"]
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:link {:rel "icon" :type "image/png" :href "favicon.ico"}]
   (include-css (if (env :dev) "css/site.css" "css/site.min.css"))
   (include-css "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap.min.css")])

(def loading-page
  (html5
    (head)
    [:body {:class "body-container"}
     [:div#app]
     [:script {:type "text/javascript"} "var env='" (pr-str (env :client-env)) "';"]
     (include-js "js/app.js")
     (include-js "//ajax.googleapis.com/ajax/libs/jquery/2.1.3/jquery.min.js")
     (include-js "//maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js")]))

(def cards-page
  (html5
    (head)
    [:body
     [:div#app]
     [:script {:type "text/javascript"} "var env='" (pr-str (env :client-env)) "';"]
     (include-js "js/app_devcards.js")
     (include-js "//ajax.googleapis.com/ajax/libs/jquery/2.1.3/jquery.min.js")
     (include-js "//maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js")]))

(defn get-bookmarks
  "Gets all bookmark folders and their children and returns them in an HTTP response."
  [params & [status]]
  (try
    (info "get-bookmarks")
    (let [conn (d/connect (env :database-uri))
          bookmarks (d/q '[:find (pull ?e [:db/id :bookmark/id :bookmark/name :bookmark/url
                                           :bookmark/parent {:bookmark/_parent 1}])
                           :where [?e :bookmark/id]
                           [(missing? $ ?e :bookmark/url)]] (d/db conn))
          headers {"content-type" "application/edn"}]
      {:status (or status 200)
       :headers (if (= (:csrf-token params) "true")
                  (assoc headers "csrf-token" *anti-forgery-token*)
                  headers)
       :body (str bookmarks)})
  (catch Exception e (errorf "Error %s" (.toString e)))))

(defn get-bookmark
  "Gets a bookmark and returns it in an HTTP response."
  [id & [status]]
  (try
    (infof "get-bookmark %s" id)
    (let [conn (d/connect (env :database-uri))
          bookmark (d/q `[:find (pull ?e [:db/id :bookmark/id :bookmark/name :bookmark/url
                                          :bookmark/parent {:bookmark/_parent 1}])
                          :where [?e :bookmark/id ~id]] (d/db conn))]
      {:status (or status 200)
       :headers {"content-type" "application/edn"}
       :body (str bookmark)})
    (catch Exception e (errorf "Error %s" (.toString e)))))

(defn post-bookmark
  "Posts a bookmark to the database for an HTTP request."
  [params & [status]]
  (try
    (infof "post-bookmark %s" params)
    ; Add db/id and bookmark/id if missing.
    (let [conn (d/connect (env :database-uri))
          bookmark (if (:db/id params)
                     params
                     (assoc params :db/id #db/id[:db.part/user] :bookmark/id (d/squuid)))
          response @(d/transact conn [bookmark])]
      (infof "response %s" (str response))
      {:status (or status 200)
       :headers {"content-type" "application/edn"}})
  (catch Exception e (errorf "Error %s" (.toString e)))))

(defroutes routes
           ;; Views
           (GET "/" [] loading-page)
           (GET "/add" [] loading-page)
           (GET "/about" [] loading-page)
           (GET "/cards" [] cards-page)

           ;; API
           (GET "/api/bookmarks" {params :params} [] (get-bookmarks params))
           (GET "/api/bookmark/:id" [id] (get-bookmark id))
           (POST "/api/bookmark" {params :edn-params} (post-bookmark params))

           (resources "/")
           (not-found "Not Found"))

;(def app (wrap-middleware #'routes))

(def app
  (-> #'routes
      wrap-middleware
      wrap-edn-params
      (wrap-cors :access-control-allow-origin [#"https://www.browncross.com"
                                               #"http://localhost:3000"
                                               #"http://localhost:3449"]
                                               
                 :access-control-allow-methods [:get :post])))
