(ns bookmarx.handler
  (:require [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [include-js include-css html5]]
            [bookmarx.middleware :refer [wrap-middleware]]
            [config.core :refer [env]]
            [datomic.api :as d]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.edn :refer :all]))

(def uri "datomic:sql://bookmarx?jdbc:mysql://localhost:3306/datomic?user=datomic&password=datomic")

(def mount-target
  [:div#app])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))])

(def loading-page
  (html5
    (head)
    [:body {:class "body-container"}
     [:div#app]
     (include-js "/js/app.js")]))

(def cards-page
  (html5
    (head)
    [:body
     [:div#app]
     (include-js "/js/app_devcards.js")]))

(defn get-bookmarks
  "Gets all bookmark folders and their children and returns them in an HTTP response."
  [_ & [status]]
  (let [conn (d/connect uri)
        bookmarks (d/q '[:find (pull ?e [:db/id :bookmark/id :bookmark/name :bookmark/url
                                         :bookmark/parent {:bookmark/_parent 1}])
                         :where [?e :bookmark/id]
                         [(missing? $ ?e :bookmark/url)]] (d/db conn))]
    {:status (or status 200)
     :headers {"Content-Type" "application/edn"}
     :body (pr-str bookmarks)}))

(defn get-bookmark
  "Gets a bookmark and returns it in an HTTP response."
  [id & [status]]
  (let [conn (d/connect uri)
        bookmark (d/q `[:find (pull ?e [:db/id :bookmark/id :bookmark/name :bookmark/url
                                        :bookmark/parent {:bookmark/_parent 1}])
                        :where [?e :bookmark/id ~id]] (d/db conn))]
    {:status (or status 200)
     :headers {"Content-Type" "application/edn"}
     :body (pr-str bookmark)}))

(defn post-bookmark
  "Posts a bookmark to the database for an HTTP request."
  [body & [status]]
  (let [conn (d/connect uri)
        id @(d/transact conn body)]
    {:status (or status 200)
     :headers {"Content-Type" "application/edn"}
     :body {:db/id id}}))

(defroutes routes
           (GET "/" [] loading-page)
           (GET "/about" [] loading-page)
           (GET "/cards" [] cards-page)
           (GET "/api/bookmarks" [] (get-bookmarks ""))
           (GET "/api/bookmarks/:id" [id] (get-bookmark id))
           (POST "/api/bookmarks" {body :body} (post-bookmark (slurp body)))
           (resources "/")
           (not-found "Not Found"))

;(def app (wrap-middleware #'routes))

(def app
  (-> #'routes
      wrap-middleware
      wrap-edn-params
      (wrap-cors :access-control-allow-origin [#"http://localhost"
                                               #"http://localhost:3449"
                                               #"http://localhost:3000"]
                 :access-control-allow-methods [:get :post])))
