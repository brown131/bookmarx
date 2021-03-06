(ns bookmarx.folder
  (:require [reagent.session :as session]
            [bookmarx.common :refer [path]]
            [bookmarx.header :as header]))

(defn bookmark-tree "Render a bookmark in a tree."
  [{:keys [bookmark/id bookmark/title bookmark/children] :as bm}]
  (let [add-id (session/get-in [:add :bookmark/id])
        add-parent-id (session/get-in [:add :bookmark/parent-id])]
    [:div.bookmark_children {:key (str id "-key")}
     [:label.bookmark_folder-icon-close {:key (str id "-icon-key")}]
     (if (= id add-parent-id)
       [:label.active {:key (str id "-title-key") :href (path "/add")} title]
       [:a.bookmark {:key (str id "-title-key")
                     :on-click #(session/update-in! [:add :bookmark/parent-id] (fn [_] id))
                     :href (path "/add")} title])
     (when-let [folders (remove #(or (:bookmark/url (session/get %))
                                     (nil? (session/get %))
                                     (= % add-id)
                                     (= % -1)) children)]
       [:ul.nav.nav-pills.nav-stacked {:key (str id "-children-key")}
        (doall (map #(bookmark-tree (session/get %)) folders))])]))

(defn folder-page "Select a parent folder for a bookmark."
  []
  [:div.col-sm-12
     [header/header]
     [bookmark-tree (session/get 1)]])
