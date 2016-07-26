(ns bookmarx.header
  (:require [reagent.session :as session]
            [bookmarx.env :refer [env]]))

(enable-console-print!)

(defn header "Render the header for the page."
  ([] (header false))
  ([full?]
  [:span
   [:nav.header-nav
    [:div.container-fluid
     [:span.header-navbar "Bookmarx"]
     [:span.header-navbar.header-star]
     (when full?
      [:form.navbar-form {:role "search"}
        [:span.navbar-right
         [:input {:type "text" :placeholder "Search"}]
         [:button.header-button
         [:span.glyphicon.glyphicon-search {:color "white"}]]
         [:span.dropdown
          [:button.header-button.dropdown-toggle {:data-toggle "dropdown"}
           [:span.glyphicon.glyphicon-menu-hamburger {:color "white"}]]
          [:ul.dropdown-menu
           [:li [:a {:href (str (:prefix env) "/about")} "About..."]]
           [:li [:a {:href (str (:prefix env) "/add") :on-click #(session/remove! :add)} "Add Bookmark..."]]
           [:li [:a {:href "#"} "Show"]]
           [:li [:a {:href "#"} "Sort"]]]]]])]]]))
