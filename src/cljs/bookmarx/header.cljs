(ns bookmarx.header
  (:require [reagent.session :as session]
            [goog.dom :as dom]
            [bookmarx.common :refer [env]]))

(enable-console-print!)

(defn style-elem
  "Style an element"
  [element attrs]
  (dom/setProperties element
                     (js-obj "style" (apply str (interpose ";"
                                                             (map #(str (name %1) ":" %2)
                                                                  (keys attrs) (vals attrs)))))))

(defn header "Render the header for the page."
  ([] (header false))
  ([full?]
  [:span
   [:nav.header-nav
    [:div.container-fluid
     [:a.header-navbar {:href (str (:prefix env) "/")} "Bookmarx" [:span.header-star]]
     (when full?
      [:form.navbar-form {:role "search"}
        [:span.navbar-right
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
           [:li.dropdown-submenu
             {:on-click (fn [e]
                             ;;(.log js/console (dom/getElement "toggle-item"))
                             (style-elem (dom/getElement "toggle-item") {:display "none"})
                             ;;(.toggle (dom/getElement "show-item"))
                             (.stopPropagation e)
                             (.preventDefault e)
                             false)} "Show" [:span.caret]
                [:ul
                  [:li {:id "toggle-item"} [:a {:href "#"} "URL"]]
                  [:li [:a {:href "#"} "Created"]]
                  [:li [:a {:href "#"} "Last Visited"]]
                  [:li [:a {:href "#"} "Visited"]]]]
           [:li [:a {:href "#"} "Sort"]]]]]])]]]))
