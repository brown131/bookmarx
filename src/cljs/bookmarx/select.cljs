(ns bookmarx.select
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [goog.window :as gwin]
            [cljs-http.client :as http]))

(defn -select-folder "Select a folder."
  [id]
  (session/put! :selected id)
  (accountant/navigate! "/add"))

(defn header "Render the header for the page."
  []
  [:span
   [:nav {:class "header-nav"}
    [:div {:class "container-fluid"}
     [:span {:class "header-navbar"} "Bookmarx"]
     [:span {:class "header-navbar header-star"}]]]])

(defn select "Select a parent folder for a bookmark."
  [mark]
  (let [{:keys [db/id bookmark/name bookmark/url bookmark/rating bookmark/_parent]} mark]
    (when-not url
      (let [{:keys [bookmark/_parent open?]} (session/get id)]
        [:div {:class "bookmark_children" :key (str id "-key")}
         [:span {:class "bookmark_arrow" :key (str id "-arrow-key")}]
         [:span {:class "bookmark_folder-icon-open" :aria-hidden "true" :key (str id "-icon-key")}]
         [:a {:class "bookmark" :key (str id "-name-key") :on-click #(-select-folder id)} name]
         [:ul {:class "nav nav-pills nav-stacked":key (str id "-children-key")}
          (doall (map #(select %) _parent))]]))))
