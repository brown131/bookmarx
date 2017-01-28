(ns bookmarx.pages
  (:require [hiccup.page :refer [include-js include-css html5]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [ring.util.response :as r]
            [config.core :refer [env]]))

(defn header []
  [:head
   [:title "Bookmarx"]
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:link {:rel "icon" :type "image/png" :href "favicon.ico"}]
   (include-css (if (env :dev) "css/site.css" "css/site.min.css"))
   (include-css "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css")])

(def page-template
  (html5
    (header)
    [:body {:class "body-container"}
     [:div#app]
     (include-js "js/app.js")
     (include-js "https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js")
     (include-js "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js")]))

(defn login-page-handler
  [request]
  (let [opts {:path (env :prefix)}]
    (-> (r/response page-template)
        (r/header "content-type" "text/html; charset=utf-8")
        (r/set-cookie "prefix" (env :prefix) opts))))

(defn secured-page-handler
  [request]
  (let [opts {:path (env :prefix)}]
    (-> (r/response page-template)
        (r/header "content-type" "text/html; charset=utf-8")
        (r/set-cookie "new-hours" (env :new-hours) opts)
        (r/set-cookie "last-visited-hours" (env :last-visited-hours) opts)
        (r/set-cookie "auth-token-hours" (env :auth-token-hours) opts)
        (r/set-cookie "cache-refresh-hours" (env :cache-refresh-hours) opts))))
