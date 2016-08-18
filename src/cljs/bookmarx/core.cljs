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
            [bookmarx.select :as select]
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

(secretary/defroute (str (:prefix env) "/select") []
                    (session/put! :current-page #'select/select-page))

(secretary/defroute (str (:prefix env) "/search") []
                    (session/put! :current-page #'search/search-page))

(defn current-page "Render the current page."
  []
  [:div [(session/get :current-page)]])

(defn mount-root "Mount the root node of the DOM with the current page."
  []
  (reagent/render [:div [current-page]] (.getElementById js/document "app")))

(defn init! "Load the bookmarks from the server and set the state for the application."
  []
  (go (let [response (<! (http/get (str (:host-url env) (:prefix env) "/api/bookmarks")
                                  {:query-params {:csrf-token true} :with-credentials? false}))
            bookmarks (mapv #(sort-folder-children (apply merge %) 
                                                   (fn [b] (str/upper-case (:bookmark/title b)))) 
                            (:body response))
            root (:db/id (first (filter #(nil? (:bookmark/parent %)) bookmarks)))
            active (cookies/get "active" root)]
        (set-active active)
        (session/put! :root root)
        (session/put! :csrf-token (get-in response [:headers "csrf-token"]))
        (doall (map #(session/put! (:db/id %) %) bookmarks))))
  (secretary/set-config! :prefix "/bookmark")
  (accountant/configure-navigation!
   {:nav-handler (fn [path] (secretary/dispatch! path))
    :path-exists? (fn [path] (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))
