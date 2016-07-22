(ns bookmarx.header
  (:require [reagent.session :as session]
            [bookmarx.env :refer [env]]))

(enable-console-print!)

(defn header "Render the header for the page."
  ([] (header false))
  ([full?]
  [:span
   [:nav {:class "header-nav"}
    [:div {:class "container-fluid"}
     [:span {:class "header-navbar"} "Bookmarx"]
     [:span {:class "header-navbar header-star"}]
     (when full?
      [:form {:class "navbar-form" :role "search"}
        [:span {:class "navbar-right"}
         [:input {:type "text" :placeholder "Search"}]
         [:button {:class "header-button"}
         [:span {:class "glyphicon glyphicon-search" :color "white"}]]
         [:span {:class "dropdown"}
          [:button {:class "header-button dropdown-toggle" :data-toggle "dropdown"}
           [:span {:class "glyphicon glyphicon-menu-hamburger" :color "white"}]]
          [:ul {:class "dropdown-menu"}
           [:li [:a {:href (str (:prefix env) "/about")} "About..."]]
           [:li [:a {:href (str (:prefix env) "/add") :on-click #(session/remove! :add)} "Add Bookmark..."]]
           [:li [:a {:href "#"} "Show"]]
           [:li [:a {:href "#"} "Sort"]]]]]])]]]))
