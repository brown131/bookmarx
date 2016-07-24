(ns bookmarx.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [bookmarx.env :refer [env]]
            [bookmarx.about :as about]
            [bookmarx.add :as add]
            [bookmarx.home :as home])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defn sort-folder-children "Sort the children of a folder by a sort key."
  [folder sort-key]
  (let [[l f] (map vec ((juxt filter remove) #(:bookmark/url %) (:bookmark/_parent folder)))]
    (update-in folder [:bookmark/_parent]
               #(into [] (concat (sort-by sort-key f) (sort-by sort-key l))))))

(secretary/defroute (str (:prefix env) "/") [] (session/put! :current-page #'home/home-page))

(secretary/defroute (str (:prefix env) "/about") [] (session/put! :current-page #'about/about-page))

(secretary/defroute (str (:prefix env) "/add") [] (session/put! :current-page #'add/add-page))

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
            bookmarks (mapv #(sort-folder-children (apply merge %) :bookmark/name) (:body response))
            active (:db/id (first (filter #(nil? (:bookmark/parent %)) bookmarks)))]
        (session/put! :active active)
        (session/put! :csrf-token (get-in response [:headers "csrf-token"]))
        (doall (map #(session/put! (:db/id %) %) bookmarks))))
  (secretary/set-config! :prefix "/bookmark")
  (accountant/configure-navigation!
   {:nav-handler (fn [path] (secretary/dispatch! path))
    :path-exists? (fn [path] (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))
