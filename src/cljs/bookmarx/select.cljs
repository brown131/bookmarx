(ns bookmarx.select
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [goog.window :as gwin]
            [cljs-http.client :as http]
            [bookmarx.env :refer [env]]
            [bookmarx.header :as header]))

(defn -select-folder "Select a folder."
  [id]
  (session/put! :selected id)
  (accountant/navigate! (str (:prefix env) "/add")))

(defn select "Select a parent folder for a bookmark."
  [mark]
  [header/header]
  (let [{:keys [db/id bookmark/name bookmark/url bookmark/rating bookmark/_parent]} mark]
    (when-not url
      (let [{:keys [bookmark/_parent open?]} (session/get id)]
        [:div.bookmark_children {:key (str id "-key")}
         [:span.bookmark_arrow {:key (str id "-arrow-key")}]
         [:span.bookmark_folder-icon-open {:aria-hidden "true" :key (str id "-icon-key")}]
         [:a.bookmark {:key (str id "-name-key") :on-click #(-select-folder id)} name]
         [:ul.nav.nav-pills.nav-stacked {:key (str id "-children-key")}
          (doall (map #(select %) _parent))]]))))
