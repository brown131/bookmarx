(ns bookmarx.header
  (:require [reagent.cookies :as cookies]
            [reagent.session :as session]
            [taoensso.timbre :as log]
            [bookmarx.client :refer [empty-trash]]
            [bookmarx.common :refer [path server-path]]))

(enable-console-print!)

(defn logout []
  ;; Remove local cookies.
  (cookies/remove! "auth-token")
  (cookies/remove! "active")
  (cookies/remove! "revision")

  ;; Remove cookies from server.
  (cookies/remove! "host-url")
  (cookies/remove! "prefix")
  (cookies/remove! "new-hours")
  (cookies/remove! "last-visited-hours")
  (cookies/remove! "auth-token-hours")
  (cookies/remove! "cache-refresh-hours")

  (.removeItem (.-localStorage js/window) "bookmarks")
  (session/put! :revision 0))

(defn header "Render the header for the page."
  ([] (header false))
  ([full?]
  [:span
   [:nav.header-nav
    [:div.container-fluid
     [:a.header-navbar {:href (path "/")} "Bookmarx" [:span.header-star]]
     (when full?
      [:form.navbar-form {:role "search"}
        [:span.navbar-right {:style {:font-weight "normal"}}
         [:input {:type "text" :placeholder "Search" :key "search" :value (session/get :search) 
                  :on-change #(session/put! :search (-> % .-target .-value))}] 
         [:a.header-button {:href (path "/search")}
          [:span.glyphicon.glyphicon-search {:color "white"}]]
         [:span.dropdown
          [:button.header-button.dropdown-toggle {:data-toggle "dropdown"}
           [:span.glyphicon.glyphicon-menu-hamburger {:color "white"}]]
          [:ul.dropdown-menu
           [:li [:a {:href (path "/about")} "About…"]]
           [:li [:a {:href (path "/add") :on-click #(session/remove! :add)} "Add Bookmark…"]]
           [:li [:a {:href (path "/settings")} "Settings…"]]
           [:li [:a {:href "#" :on-click empty-trash} "Empty trash"]]
           [:li [:a {:href (path "/login") :on-click logout} "Logout"]]]]]])]]]))

