(ns bookmarx.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.cookies :as cookies]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [cljs.core.async :refer [<!]]
            [cljs.reader :refer [read-string]]
            [cljs-http.client :as http]
            [bookmarx.about :as about]
            [bookmarx.add :as add]
            [bookmarx.common :refer [env set-active!]]
            [bookmarx.home :as home]
            [bookmarx.folder :as folder]
            [bookmarx.icon :as icon]
            [bookmarx.search :as search])
  (:require-macros
    [cljs.core.async.macros :refer [go]]))

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

(defn load-bookmarks "Request bookmarks from the server and set local state."
  [rev]
  (go (time (let [url (str (:host-url env) (:prefix env) "/api/bookmarks/since/" rev)
            response (<! (http/get url {:query-params {:csrf-token true} :with-credentials? false}))
            bookmarks (into {} (map #(vector (:bookmark/id %) %) (get-in response [:body :bookmarks])))
            revision (get-in response [:body :revision])]
        ;; Set the session state.
        (session/put! :csrf-token (get-in response [:headers "csrf-token"]))
        (session/put! :revision (js/parseInt revision))
        (reset! session/state (merge @session/state bookmarks))

        ;; Store the response locally.
        (cookies/set! "revision" revision {:path (:prefix env)
                                           :max-age (* (:cache-refresh-hours env) 60 60)})
        (when-not (= rev revision)
          (.setItem (.-localStorage js/window) "bookmarks"
                    (pr-str (into {} (remove #(keyword? (key %)) @session/state)))))))))

(defn current-page "Render the current page."
  []
  [:div [(session/get :current-page)]])

(defn mount-root "Mount the root node of the DOM with the current page."
  []
  (reagent/render [:div [current-page]] (.getElementById js/document "app")))

(defn init! "Set the state for the application."
  []
  (let [revision (js/parseInt (cookies/get "revision" 0))]
    (when-not (zero? revision)
      (reset! session/state (merge @session/state
                                   (read-string (.getItem (.-localStorage js/window) "bookmarks")))))
    (load-bookmarks revision))
  (set-active! 1)
  (secretary/set-config! :prefix "/bookmark")
  (accountant/configure-navigation!
   {:nav-handler #(secretary/dispatch! %)
    :path-exists? #(secretary/locate-route %)})
  (accountant/dispatch-current!)
  (mount-root))
