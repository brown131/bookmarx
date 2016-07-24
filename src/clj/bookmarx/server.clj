(ns bookmarx.server
  (:require [bookmarx.handler :refer [app]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]
            [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

(timbre/refer-timbre)

 (defn -main [& args]
   (timbre/set-config! (dissoc (env :log-config) :fname))
   (timbre/merge-config!
     {:appenders {:spit (timbre/spit-appender {:fname (:fname (env :log-config))})}})

   (let [port (Integer/parseInt (or (env :port) "3000"))]
     (run-jetty app {:port port :join? false})))
