(ns bookmarx.search
  (:require [clojure.string :as str]
            [reagent.session :as session]
            [accountant.core :as accountant]
            [goog.window :as gwin]
            [taoensso.timbre :as log]
            [bookmarx.common :refer [env]]
            [bookmarx.header :as header]))
    
(defn search-bookmarks "Search the bookmarks for the given text."
  ([text bookmarks] (search-bookmarks text bookmarks []))
  ([text bookmarks matches]
   (cond (nil? text) []
         (empty? bookmarks) matches
         (nil? (:bookmark/url (first bookmarks)))
         (recur text (rest bookmarks)
                (search-bookmarks text (-> bookmarks first :bookmark/id session/get :bookmark/children)
                                  matches))
         (or (str/index-of (str/upper-case (:bookmark/title (first bookmarks))) (str/upper-case text))
             (str/index-of (str/upper-case (:bookmark/url (first bookmarks))) (str/upper-case text)))
         (recur text (rest bookmarks) (conj matches (first bookmarks)))
         :else (recur text (rest bookmarks) matches))))

(defn bookmark-link "Render a bookmark link."
  [bookmark]
  (let [{:keys [bookmark/id bookmark/title bookmark/url bookmark/rating
                bookmark/icon bookmark/icon-color]} bookmark]
   [:div.bookmark_children {:key (str id "-key")}
    (if icon
      [:a {:class (str "glyphicon " icon) :aria-hidden "true"
           :style {:color (if icon-color icon-color "Black")}
           :key (str id "-icon-key") :on-click #(session/put! :add bookmark)
           :href (str (:prefix env) "/add")}]
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
   (doall (map #(bookmark-link %)
               (search-bookmarks (session/get :search)
                                 (:bookmark/children (session/get 1)))))
   [:div [:a {:href (str (:prefix env) "/")} "go to the home page"]]])
