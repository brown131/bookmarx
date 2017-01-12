(ns bookmarx.server
  (:require [compojure.core :refer [GET POST PUT DELETE defroutes]]
            [compojure.route :refer [not-found resources]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.edn :refer :all]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.transit :refer [wrap-transit-response]]
            [prone.middleware :refer [wrap-exceptions]]
            [bookmarx.handler :refer :all]
            [bookmarx.pages :refer :all])
  (:gen-class))

(timbre/refer-timbre)

(defroutes routes
           ;; Authentication


           ;; Views
           (GET "/" [] loading-page)
           (GET "/add" [] loading-page)
           (GET "/about" [] loading-page)
           (GET "/cards" [] cards-page)
           (GET "/folder" [] loading-page)
           (GET "/icon" [] loading-page)
           (GET "/search" [] loading-page)

           ;; REST API
           (GET "/api/bookmarks/since/:rev" [rev] (get-bookmarks-since rev))
           (GET "/api/bookmarks" [] (get-bookmarks))
           (POST "/api/bookmarks" {bookmark :edn-params} (post-bookmark bookmark))
           (PUT "/api/bookmarks/:id" {{id :id} :route-params bookmark :edn-params} [] (put-bookmark id bookmark))
           (DELETE "/api/bookmarks/:id" [id] (delete-bookmark id))

           (resources "/")
           (not-found "Not Found"))

(defn wrap-middleware [handler]
  (let [wrapper (wrap-defaults handler site-defaults)]
    (if (env :dev)
      (-> wrapper
          wrap-exceptions
          wrap-reload)
      wrapper)))

(def app
  (-> #'routes
      wrap-middleware
      wrap-edn-params
      wrap-transit-response
      (wrap-cors :access-control-allow-origin [#"https://www.browncross.com"
                                               #"http://localhost:3000"
                                               #"http://localhost:3449"]
                 :access-control-allow-methods [:get :post :put :delete])))

 (defn -main [& args]
   (timbre/set-config! (dissoc (env :log-config) :fname))
   (timbre/merge-config!
     {:appenders {:spit (timbre/spit-appender {:fname (:fname (env :log-config))})}})

   (let [port (Integer/parseInt (or (env :port) "3000"))]
     (run-jetty app {:port port :join? false})))
