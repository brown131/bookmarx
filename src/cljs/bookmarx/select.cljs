(ns bookmarx.select
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [goog.window :as gwin]
            [cljs-http.client :as http]
            [bookmarx.env :refer [env]]
            [bookmarx.header :as header]))

(defn -select-folder "Select the folder to move/add the bookmark to."
  [id]
  (session/update-in! [:add :bookmark/parent :db/id] (fn [_] id))
  (accountant/navigate! (str (:prefix env) "/add")))

(defn bookmark-tree "Render a bookmark in a tree."
  [{:keys [db/id bookmark/name bookmark/url bookmark/rating bookmark/_parent]}]
  (when-not (or url (= id (session/get-in [:add :db/id])))
    (let [{:keys [bookmark/_parent]} (session/get id)]
      [:div.bookmark_children {:key (str id "-key")}
       [:label.bookmark_folder-icon-close {:key (str id "-icon-key")}]
       (if (= id (session/get-in [:add :bookmark/parent :db/id]))
         [:label.active {:key (str id "-name-key")
                         :on-click #(accountant/navigate! (str (:prefix env) "/add"))} name]
         [:a.bookmark {:key (str id "-name-key") :on-click #(-select-folder id)} name])
       [:ul.nav.nav-pills.nav-stacked {:key (str id "-children-key")}
        (doall (map #(bookmark-tree %) _parent))]])))

(defn select-page "Select a parent folder for a bookmark."
  []
  [:div.col-sm-12
     [header/header]
     [bookmark-tree (session/get (session/get :root))]
    [:div "add" (session/get :add)]
   ])


