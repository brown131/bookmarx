(ns bookmarx.env
  (:require [cljs.reader :refer [read-string]]
            [reagent.cookies :as cookies]
            [reagent.session :as session]))

(def env (read-string js/env))

(defn set-active "Set the folder that is active in the session and as a cookie."
  [active]
  (cookies/set! "active" active {:path (:prefix env)})
  (session/put! :active (js/parseInt active)))

(defn get-active "Get the active folder from the cookie or else the session."
  []
  (cookies/get "active" (session/get :active)))

(defn sort-folder-children "Sort the children of a folder by a sort key."
  [folder sort-key]
  (let [[l f] (map vec ((juxt filter remove) #(:bookmark/url %) (:bookmark/_parent folder)))]
    (update-in folder [:bookmark/_parent]
               #(into [] (concat (sort-by sort-key f) (sort-by sort-key l))))))
