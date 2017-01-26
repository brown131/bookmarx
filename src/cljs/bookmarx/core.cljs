(ns bookmarx.core
  (:require [reagent.core :as reagent]
            [reagent.cookies :as cookies]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [cemerick.url :as url]
            [cljs.reader :refer [read-string]]
            [bookmarx.about :as about]
            [bookmarx.add :as add]
            [bookmarx.common :refer [path set-cookie! get-cookie load-bookmarks]]
            [bookmarx.home :as home]
            [bookmarx.folder :as folder]
            [bookmarx.icon :as icon]
            [bookmarx.login :as login]
            [bookmarx.search :as search])
  (:require-macros
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

(defn current-page "Render the current page."
  []
  [:div [(session/get :current-page)]])

(defn mount-root "Mount the root node of the DOM with the current page."
  []
  (reagent/render [:div [current-page]] (.getElementById js/document "app")))

(defn init! "Set the state for the application."
  []
  ;; Load bookmarks.
  (when (cookies/get "auth-token")
    (let [revision (js/parseInt (cookies/get "revision" 0))]
      (println "revision" revision)
      (when-not (zero? revision)
        (let [bookmarks (read-string (.getItem (.-localStorage js/window) "bookmarks"))]
          (session/put! :revision (js/parseInt revision))
          (reset! session/state (merge @session/state bookmarks))))
      (load-bookmarks revision)))

  (set-cookie! :csrf-token (url/url-decode (get-cookie :csrf-token)))
  (when-not (= (path "/add") (:path (url/url (-> js/window .-location .-href))))
    (set-cookie! :active 1))

  ;; Setup navigation.
  (secretary/set-config! :prefix "/bookmark")
  (accountant/configure-navigation!
   {:nav-handler #(secretary/dispatch! %)
    :path-exists? #(secretary/locate-route %)})
  (accountant/dispatch-current!)
  (mount-root))
