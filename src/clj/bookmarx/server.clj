(ns bookmarx.server
  (:require [compojure.core :refer [context GET POST PUT DELETE defroutes]]
            [compojure.route :refer [not-found resources]]
            [config.core :refer [env]]
            [taoensso.timbre :as t]
            [taoensso.timbre.appenders.core :refer [spit-appender]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.edn :refer [wrap-edn-params]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [prone.middleware :refer [wrap-exceptions]]
            [bookmarx.auth :refer [wrap-auth-token wrap-authentication]]
            [bookmarx.ds :refer [cache-bookmarks]]
            [bookmarx.handler :as h]
            [bookmarx.pages :refer [page-handler]])
  (:gen-class))

(defroutes public-routes
  ;; Authentication
  (GET "/login" [] page-handler)
  (GET "/api/csrf-token" [] (h/set-csrf-token {:status 200}))
  (POST "/login" {credentials :edn-params} (h/post-login credentials)))

(defroutes secured-routes
  ;; Views
  (context "/" []
           (GET "/" [] page-handler)
           (GET "/add" [] page-handler)
           (GET "/about" [] page-handler)
           (GET "/folder" [] page-handler)
           (GET "/icon" [] page-handler)
           (GET "/logout" [] page-handler)
           (GET "/search" [] page-handler)
           (GET "/settings" [] page-handler))

  ;; REST API
  (context "/api" []
           (GET "/bookmarks/since/:rev" [rev] (-> (h/get-bookmarks-since rev)
                                                  (h/set-csrf-token)))
           (GET "/settings" [] (h/get-settings))
           (POST "/bookmarks" {bookmark :edn-params} (h/post-bookmark bookmark))
           (POST "/settings" {settings :edn-params} (h/post-settings settings))
           (PUT "/bookmarks/visit/:id" [id] (h/put-bookmark-visit id))
           (PUT "/bookmarks/:id" {{id :id} :route-params bookmark :edn-params} (h/put-bookmark id bookmark))
           (DELETE "/bookmarks/trash" [] (h/delete-trash))
           (DELETE "/bookmarks/:id" [id] (h/delete-bookmark id)))

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
      (wrap-restful-format :formats [:transit-json])
      (wrap-cors :access-control-allow-origin [#"http[s]*://www.browncross.com" #"http://localhost:\d+"]
                 :access-control-allow-methods [:get :post :put :delete])))

 (defn -main [& _]
   (t/set-config! (dissoc (env :log-config) :fname))
   (t/merge-config!
    {:appenders {:spit (spit-appender {:fname (:fname (env :log-config))})}})

   (cache-bookmarks)

   (let [port (Integer/parseInt (str (or (env :port) 3449)))]
     (run-jetty app {:port port :join? false})))
