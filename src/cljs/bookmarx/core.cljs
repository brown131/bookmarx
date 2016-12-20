(ns bookmarx.core
  (:require [clojure.string :as str]
            [reagent.core :as reagent :refer [atom]]
            [reagent.cookies :as cookies]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [bookmarx.about :as about]
            [bookmarx.add :as add]
            [bookmarx.common :refer [env load-bookmarks set-active!]]
            [bookmarx.home :as home]
            [bookmarx.folder :as folder]
            [bookmarx.icon :as icon]
            [bookmarx.search :as search]))

(enable-console-print!)

(secretary/defroute (str (:prefix env) "/") []
                    (session/put! :current-page #'home/home-page))

(secretary/defroute (str (:prefix env) "/about") []
                    (session/put! :current-page #'about/about-page))

(secretary/defroute (str (:prefix env) "/add") []
                    (session/put! :current-page #'add/add-page))

(secretary/defroute (str (:prefix env) "/folder") []
                    (session/put! :current-page #'folder/folder-page))

(secretary/defroute (str (:prefix env) "/icon") []
                    (session/put! :current-page #'icon/icon-page))

(secretary/defroute (str (:prefix env) "/search") []
                    (session/put! :current-page #'search/search-page))

(defn current-page "Render the current page."
  []
  [:div [(session/get :current-page)]])

(defn mount-root "Mount the root node of the DOM with the current page."
  []
  (reagent/render [:div [current-page]] (.getElementById js/document "app")))

(defn init! "Set the state for the application."
  []
  (load-bookmarks)
  (set-active! 1)
  (secretary/set-config! :prefix "/bookmark")
  (accountant/configure-navigation!
   {:nav-handler (fn [path] (secretary/dispatch! path))
    :path-exists? (fn [path] (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))
