(ns bookmarx.header
  (:require [reagent.session :as session]
            [bookmarx.common :refer [env]]))

(enable-console-print!)

(defn header "Render the header for the page."
  ([] (header false))
  ([full?]
  [:span
   [:nav.header-nav
    [:div.container-fluid
     [:a.header-navbar {:href (str (:prefix env) "/")} "Bookmarx" [:span.header-star]]
     (when full?
      [:form.navbar-form {:role "search"}
        [:span.navbar-right {:style {:font-weight "normal"}}
         [:input {:type "text" :placeholder "Search" :key "search" :value (session/get :search) 
                  :on-change #(session/put! :search (-> % .-target .-value))}] 
         [:a.header-button {:href (str (:prefix env) "/search")}
          [:span.glyphicon.glyphicon-search {:color "white"}]]
         [:span.dropdown
          [:button.header-button.dropdown-toggle {:data-toggle "dropdown"}
           [:span.glyphicon.glyphicon-menu-hamburger {:color "white"}]]
          [:ul.dropdown-menu
           [:li [:a {:href (str (:prefix env) "/about")} "About..."]]
           [:li [:a {:href (str (:prefix env) "/add")
                     :on-click #(session/remove! :add)} "Add Bookmark..."]]
           [:li [:a {:href "#"} "Show" [:span.caret]]]
           [:li.dropdown-submenu
            {:on-click (fn [e]
                         (.toggle (.-classList (.getElementById js/document "submenu"))
                                  "dropdown-menu")
                         ;; Can't prevent dropdown from closing due to Bootstrap 3 bug.
                         (.preventDefault e)
                         false)}
            [:a {:href "#"} "Sort" [:span.caret]]
            [:ul.dropdown-menu {:id "submenu" :display "block"}
             [:li [:a {:href "#"} "Title"]]
             [:li [:a {:href "#"} "Date Created"]]]]
           [:li [:a {:href "#"} "Empty trash"]]]]]])]]]))

