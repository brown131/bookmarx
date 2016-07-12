(ns bookmarx.about
  (:require [reagent.session :as session]
            [bookmarx.header :as header]))

(defn about-page "Render the About page."
  []
  [:div {:class "col-sm-12"}
   [header/header]
   [:h2 "About bookmarx"]
   [:div [:a {:href "/"} "go to the home page"]]])

