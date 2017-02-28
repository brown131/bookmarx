(ns bookmarx.pages
  (:require [hiccup.page :refer [include-js include-css html5]]
            [ring.util.response :as r]
            [config.core :refer [env]]))

(defn header []
  [:head
   [:title "Bookmarx"]
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:link {:rel "icon" :type "image/png" :href "favicon.ico"}]
   (include-css (if (env :dev) "css/site.css" "css/site.min.css"))
   (include-css "bootstrap/3.3.7/css/bootstrap.min.css")])

(def page-template
  (html5
    (header)
    [:body {:class "body-container"}
     [:div#app]
     (include-js "js/app.js")
     (include-js "jquery/3.1.1/jquery.min.js")
     (include-js "bootstrap/3.3.7/js/bootstrap.min.js")]))

(defn page-handler
  [request]
  (-> (r/response page-template)
      (r/header "content-type" "text/html; charset=utf-8")))
