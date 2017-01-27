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
     [:script {:type "text/javascript"} "var env='" (pr-str (env :client-env)) "';"]
     (include-js "js/app.js")
     (include-js "https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js")
     (include-js "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js")]))

(defn page-handler
  [request]
  (-> (r/response page-template)
      (r/header "content-type" "text/html; charset=utf-8")
      (r/set-cookie "csrf-token" *anti-forgery-token* {:path (:prefix (env :client-env))})))
