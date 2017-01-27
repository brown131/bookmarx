(ns bookmarx.search
  (:require [clojure.string :as str]
            [reagent.session :as session]
            [goog.window :as gwin]
            [taoensso.timbre :as log]
            [bookmarx.common :refer [path]]
            [bookmarx.header :as header]))
    
(defn search-bookmarks "Search the bookmarks for the given text."
  ([text bookmark-ids] (search-bookmarks text bookmark-ids []))
  ([text bookmark-ids matches]
   (cond (nil? text) []
         (empty? bookmark-ids) matches
         (nil? (:bookmark/url (session/get (first bookmark-ids))))
         (recur text (rest bookmark-ids)
                (search-bookmarks text (-> bookmark-ids first session/get :bookmark/children) matches))
         (or (str/index-of (str/lower-case (:bookmark/title (session/get (first bookmark-ids))))
                           (str/lower-case text))
             (str/index-of (str/lower-case (:bookmark/url (session/get (first bookmark-ids))))
                           (str/lower-case text)))
         (recur text (rest bookmark-ids) (conj matches (first bookmark-ids)))
         :else (recur text (rest bookmark-ids) matches))))

(defn bookmark-link "Render a bookmark link."
  [bookmark]
  (let [{:keys [bookmark/id bookmark/title bookmark/url bookmark/rating
                bookmark/icon bookmark/icon-color]} bookmark]
   [:div.bookmark_children {:key (str id "-key")}
    (if icon
      [:a {:class (str "glyphicon " icon) :aria-hidden "true"
           :style {:color (if icon-color icon-color "Black")}
           :key (str id "-icon-key") :on-click #(session/put! :add bookmark)
           :href (path "/add")}]
      [:div.bookmark_link-icon {:aria-hidden "true" :key (str id "-icon-key")}])
    [:a.bookmark {:on-click #(gwin/open url) :key (str id "-link-key")} title]
    (when rating
      (for [i (range 0 rating)]
        [:span.bookmark_link-icon-rating {:aria-hidden "true"
                                          :key (str id "-rating" i "-key")}]))]))

(defn search-page "Render the results of a bookmark search."
  []
  [:div.col-sm-12
   [header/header]
   [:div.breadcrumbs "Search: \"" (session/get :search) "\"" ]
   (doall (map #(bookmark-link (session/get %))
               (search-bookmarks (session/get :search)
                                 (:bookmark/children (session/get 1)))))
   [:div [:a {:href (path "/")} "go to the home page"]]])
