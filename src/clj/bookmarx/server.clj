(ns bookmarx.server
  (:require [compojure.core :refer [GET POST PUT DELETE defroutes]]
            [compojure.route :refer [not-found resources]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.edn :refer :all]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.transit :refer [wrap-transit-response]]
            [prone.middleware :refer [wrap-exceptions]]
            [bookmarx.auth :refer :all]
            [bookmarx.handler :refer :all]
            [bookmarx.pages :refer :all])
  (:gen-class))

(timbre/refer-timbre)

(defroutes public-routes
           ;; Authentication
           (GET "/login" [] login-page-handler)
           (GET "/api/csrf-token" [] (get-csrf-token))
           (POST "/login" {credentials :edn-params} (create-auth-token credentials)))

(defroutes secured-routes
           ;; Views
           (GET "/" [] secured-page-handler)
           (GET "/add" [] secured-page-handler)
           (GET "/about" [] secured-page-handler)
           (GET "/folder" [] secured-page-handler)
           (GET "/icon" [] secured-page-handler)
           (GET "/logout" [] secured-page-handler)
           (GET "/search" [] secured-page-handler)

           ;; REST API
           (GET "/api/bookmarks/since/:rev" [rev] (get-bookmarks-since rev))
           (GET "/api/bookmarks" [] (get-bookmarks))
           (POST "/api/bookmarks" {bookmark :edn-params} (post-bookmark bookmark))
           (PUT "/api/bookmarks/:id" {{id :id} :route-params bookmark :edn-params}
             [] (put-bookmark id bookmark))
           (DELETE "/api/bookmarks/:id" [id] (delete-bookmark id))

           (resources "/")
           (not-found "Not Found"))

(defroutes app-routes
           (-> public-routes
               wrap-auth-token)
           (-> secured-routes
               wrap-authentication
               wrap-auth-token))

(defn wrap-middleware [handler]
  (let [wrapper (wrap-defaults handler site-defaults)]
    (if (env :dev)
      (-> wrapper
          wrap-exceptions
          wrap-reload)
      wrapper)))

(def app
  (-> #'app-routes
      wrap-anti-forgery
      wrap-middleware
      wrap-cookies
      wrap-edn-params
      wrap-transit-response
      (wrap-cors :access-control-allow-origin [#"https://www.browncross.com"
                                               #"http://localhost:\d+"]
                 :access-control-allow-methods [:get :post :put :delete])))

 (defn -main [& args]
   (timbre/set-config! (dissoc (env :log-config) :fname))
   (timbre/merge-config!
     {:appenders {:spit (timbre/spit-appender {:fname (:fname (env :log-config))})}})

   (let [port (Integer/parseInt (or (env :port) "3000"))]
     (run-jetty app {:port port :join? false})))
