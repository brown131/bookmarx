(ns bookmarx.about
  (:require [reagent.session :as session]
            [bookmarx.env :refer [env]]
            [bookmarx.header :as header]))

(defn about-page "Render the About page."
  []
  [:div.col-sm-12
   [header/header]
   [:h2 "About bookmarx"]
   [:div [:a {:href (str (:prefix env) "/")} "go to the home page"]]])

