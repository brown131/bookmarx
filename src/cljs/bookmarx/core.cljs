(ns bookmarx.core
  (:require [reagent.core :as reagent]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [cemerick.url :refer [url]]
            [bookmarx.about :as about]
            [bookmarx.add :as add]
            [bookmarx.client :refer [load-bookmarks get-settings]]
            [bookmarx.common :refer [path server-path set-cookie! get-cookie]]
            [bookmarx.home :as home]
            [bookmarx.folder :as folder]
            [bookmarx.icon :as icon]
            [bookmarx.login :as login]
            [bookmarx.search :as search]
            [bookmarx.settings :as settings])
  (:require-macros
    [bookmarx.env :refer [cljs-env]]
    [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(defn route [res-path page]
  (secretary/defroute (path res-path) [] (session/put! :current-page page)))

(route "/" #'home/home-page)
(route "/about" #'about/about-page)
(route "/add" #'add/add-page)
(route "/folder" #'folder/folder-page)
(route "/icon" #'icon/icon-page)
(route "/login" #'login/login-page)
(route "/search" #'search/search-page)
(route "/settings" #'settings/settings-page)

(defn current-page "Render the current page."
  []
  [:div [(session/get :current-page)]])

(defn mount-root "Mount the root node of the DOM with the current page."
  []
  (reagent/render [:div [current-page]] (.getElementById js/document "app")))

(defn init! "Set the state for the application."
  []
  ;; Load bookmarks if authenticated.
  (load-bookmarks)
  (get-settings)

  ;; Set the folder to root if not adding.
  (when-not (= (path "/add") (:path (url (-> js/window .-location .-href))))
    (set-cookie! :active 1))

  ;; Setup navigation.
  (secretary/set-config! :prefix (cljs-env :prefix))
  (accountant/configure-navigation!
   {:nav-handler #(secretary/dispatch! %)
    :path-exists? #(secretary/locate-route %)})
  (accountant/dispatch-current!)
  (mount-root))
