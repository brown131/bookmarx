(ns bookmarx.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]
              [cljs-http.client :as http]
              [cljs.core.async :refer [<!]]
              [bookmarx.route :as route])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defn sort-folder-children "Sort the children of a folder by a sort key."
  [folder sort-key]
  (let [[l f] (map vec ((juxt filter remove) #(:bookmark/url %) (:bookmark/_parent folder)))]
    (update-in folder [:bookmark/_parent]
               #(into [] (concat (sort-by sort-key f) (sort-by sort-key l))))))

(defn mount-root "Mount the root node of the DOM."
  []
  (reagent/render [route/current-page] (.getElementById js/document "app")))

(defn init! "Load the bookmarks from the database and set the state for the application."
  []
  (go (let [body (:body (<! (http/get (str "https://www.browncross.com" "/api/bookmarks")
                                      {:with-credentials? false})))
            bookmarks (mapv #(sort-folder-children (apply merge %) :bookmark/name) body)
            active (:db/id (first (filter #(nil? (:bookmark/parent %)) bookmarks)))]
        (session/put! :active active)
        (doall (map #(session/put! (:db/id %) %) bookmarks))
        (accountant/configure-navigation!
          {:nav-handler (fn [path] (secretary/dispatch! path))
           :path-exists? (fn [path] (secretary/locate-route path))})
        (accountant/dispatch-current!)
        (mount-root))))
