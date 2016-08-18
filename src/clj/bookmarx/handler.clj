(ns bookmarx.handler
  (:require [compojure.core :refer [GET POST PUT DELETE defroutes]]
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
  "Get all bookmark folders and their children and returns them in an HTTP response."
  [params & [status]]
  (try
    (info "get-bookmarks")
    (let [conn (d/connect (env :database-uri))
          bookmarks (d/q '[:find (pull ?e [:db/id :bookmark/id :bookmark/title :bookmark/url
                                           :bookmark/rating :bookmark/icon :bookmark/icon-color
                                           :bookmark/created :bookmark/visits :bookmark/parent 
                                           {:bookmark/_parent 1}])
                           :where [?e :bookmark/id]
                           [(missing? $ ?e :bookmark/url)]] (d/db conn))
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
    (let [conn (d/connect (env :database-uri))
          bookmark (d/q '[:find (pull ?e [:db/id :bookmark/id :bookmark/title :bookmark/url
                                          :bookmark/rating :bookmark/icon :bookmark/icon-color
                                          :bookmark/created :bookmark/visits :bookmark/parent 
                                          {:bookmark/_parent 1}])
                          :in $ ?uuid
                          :where [?e :bookmark/id ?uuid]] (d/db conn)
                         (java.util.UUID/fromString (:id params)))
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
    ; Add db/id and bookmark/id if missing.
    (let [conn (d/connect (env :database-uri))
          id (d/squuid)
          now (java.util.Date.)
          bookmark (assoc params :db/id #db/id[:db.part/user] :bookmark/id id
                          :bookmark/created now :bookmark/last-visited now)
          response @(d/transact conn [bookmark])]
      (infof "response %s" (str response))
      {:status (or status 200)
       :headers {"content-type" "application/edn"}
       :body (pr-str {:db/id (second (first (vec (:tempids response))))
                      :bookmark/id id})})
  (catch Exception e (errorf "Error %s" (.toString e)))))

(defn put-bookmark "Upsert a bookmark in the database for an HTTP request."
  [id params & [status]]
  (try
    (infof "put-bookmark %s %s" id params)
    (let [conn (d/connect (env :database-uri))
          response @(d/transact conn [params])]
      {:status (or status 200)})
  (catch Exception e (errorf "Error %s" (.toString e)))))

(defn delete-bookmark "Retract a bookmark in the database."
  [id & [status]]
  (try
    (infof "delete-bookmark %s" id)
    (let [conn (d/connect (env :database-uri))
          e (d/q '[:find ?e :in $ ?uuid :where [?e :bookmark/id ?uuid]] (d/db conn)
                        (java.util.UUID/fromString id))
          response @(d/transact conn `[[:db.fn/retractEntity ~(first (first e))]])]
      {:status (or status 200)})
    (catch Exception e (errorf "Error %s" (.toString e)))))

(defroutes routes
  ;; Views
  (GET "/" [] loading-page)
  (GET "/add" [] loading-page)
  (GET "/about" [] loading-page)
  (GET "/cards" [] cards-page)
  (GET "/select" [] loading-page)
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
