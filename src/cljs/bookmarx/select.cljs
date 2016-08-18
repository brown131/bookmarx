(ns bookmarx.select
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [goog.window :as gwin]
            [cljs-http.client :as http]
            [bookmarx.common :refer [env]]
            [bookmarx.header :as header]))

(defn bookmark-tree "Render a bookmark in a tree."
  [{:keys [db/id bookmark/title bookmark/url bookmark/rating bookmark/_parent]}]
  (when-not (or url (= id (session/get-in [:add :db/id])))
    (let [{:keys [bookmark/_parent]} (session/get id)]
      [:div.bookmark_children {:key (str id "-key")}
       [:label.bookmark_folder-icon-close {:key (str id "-icon-key")}]
       (if (= id (session/get-in [:add :bookmark/parent :db/id]))
         [:label.active {:key (str id "-title-key") :href (str (:prefix env) "/add")} title]
         [:a.bookmark {:key (str id "-title-key") 
                       :on-click #(session/update-in! [:add :bookmark/parent :db/id] (fn [_] id))
                       :href (str (:prefix env) "/add")} title])
       [:ul.nav.nav-pills.nav-stacked {:key (str id "-children-key")}
        (doall (map #(bookmark-tree %) _parent))]])))

(defn select-page "Select a parent folder for a bookmark."
  []
  [:div.col-sm-12
     [header/header]
     [bookmark-tree (session/get (session/get :root))]])


