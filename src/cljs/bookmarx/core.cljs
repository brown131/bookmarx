(ns bookmarx.core
  (:require [clojure.string :as str]
            [reagent.core :as reagent :refer [atom]]
            [reagent.cookies :as cookies]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [bookmarx.about :as about]
            [bookmarx.add :as add]
            [bookmarx.common :refer [env set-active sort-folder-children]]
            [bookmarx.home :as home]
            [bookmarx.folder :as folder]
            [bookmarx.icon :as icon]
            [bookmarx.search :as search])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

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

(defn get-bookmarks "Load bookmarks from the server."
  []
  (go (let [response (<! (http/get (str (:host-url env) (:prefix env) "/api/bookmarks")
                                   {:query-params {:csrf-token true} :with-credentials? false}))
            bookmarks (:body response)
            trash (:bookmark/id (first (filter #(= "~Trash" (:bookmark/title %)) bookmarks)))]
        (reset! session/state (merge bookmarks @session/state))
        (set-active 1)
        (session/put! :trash trash)
        (session/put! :csrf-token (get-in response [:headers "csrf-token"])))))

(defn init! "Set the state for the application."
  []
  (get-bookmarks)
  (secretary/set-config! :prefix "/bookmark")
  (accountant/configure-navigation!
   {:nav-handler (fn [path] (secretary/dispatch! path))
    :path-exists? (fn [path] (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))
