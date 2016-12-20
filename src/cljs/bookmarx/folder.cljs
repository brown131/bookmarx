(ns bookmarx.folder
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [taoensso.timbre :as log]
            [goog.window :as gwin]
            [cljs-http.client :as http]
            [bookmarx.common :refer [env]]
            [bookmarx.header :as header]))

(defn bookmark-tree "Render a bookmark in a tree."
  [{:keys [bookmark/id bookmark/title bookmark/rating bookmark/children]}]
  (let [add-id (session/get-in [:add :bookmark/id])
        add-parent-id (session/get-in [:add :bookmark/parent-id])]
    [:div.bookmark_children {:key (str id "-key")}
     [:label.bookmark_folder-icon-close {:key (str id "-icon-key")}]
     (if (= id add-parent-id)
       [:label.active {:key (str id "-title-key") :href (str (:prefix env) "/add")} title]
       [:a.bookmark {:key (str id "-title-key")
                     :on-click #(session/update-in! [:add :bookmark/parent-id] (fn [_] id))
                     :href (str (:prefix env) "/add")} title])
     [:ul.nav.nav-pills.nav-stacked {:key (str id "-children-key")}
      (doall (map #(bookmark-tree (session/get %)) (remove #(or (map? %) (= % add-id)) children)))]]))

(defn folder-page "Select a parent folder for a bookmark."
  []
  [:div.col-sm-12
     [header/header]
     [bookmark-tree (session/get 1)]])
