(ns bookmarx.search
  (:require [clojure.string :as str]
            [reagent.session :as session]
            [goog.window :as gwin]
            [taoensso.timbre :as log]
            [bookmarx.client :refer [visit-bookmark]]
            [bookmarx.common :refer [ticks-in-hour settings path set-cookie! parse-date get-route]]
            [bookmarx.header :as header])
  (:require-macros
    [bookmarx.env :refer [cljs-env]]))

(defn link-click [id url]
  (visit-bookmark id)
  (gwin/open url))

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

(defn breadcrumb "Renders a breadcrumb."
  [id title]
  [:a.text-muted {:on-click #(set-cookie! :active id) :key (str id "-a-key")
                  :href (path "/")} title])

(defn breadcrumbs "Render breadcrumbs for a bookmark."
  [id]
  (let [route (butlast (rest (get-route id)))
        last (last route)]
     (doall (map #(let [{:keys [bookmark/id bookmark/title]} (session/get %)]
                    [:span (breadcrumb id title)
                     (when-not (= id last) " /") " "]) route))))

(defn bookmark-link "Render a bookmark link."
  [bookmark]
  (let [{:keys [bookmark/id bookmark/title bookmark/url bookmark/rating
                bookmark/icon bookmark/icon-color bookmark/created bookmark/last-visited
                bookmark/visits]} bookmark
        {:keys [show-title show-url show-rating show-created show-last-visited show-visits show-new
                show-visited sort-on]} @settings
        now (.getTime (js/Date.))
        new-ticks (- now (* (cljs-env :new-hours) ticks-in-hour))
        last-visited-ticks (- now (* (cljs-env :last-visited-hours) ticks-in-hour))]
   [:div.bookmark_children {:key (str id "-key")}
    (if icon
      [:a {:class (str "glyphicon " icon) :aria-hidden "true"
           :style {:color (if icon-color icon-color "Black") :width "19px"}
           :key (str id "-icon-key") :on-click #(session/put! :add bookmark)
           :href (path "/add")}]
      [:a.bookmark_link-icon {:aria-hidden "true" :key (str id "-icon-key")
                              :on-click #(session/put! :add bookmark)
                              :href (path "/add")}])
    (breadcrumbs id)
    (when show-title
      [:a.bookmark {:on-click #(link-click id url) :key (str id "-link-key")} title])
    (when show-url [:a.text-muted {:on-click #(link-click id url) :key (str id "-url-key")} url])
    (when (and show-rating rating)
      (for [i (range 0 rating)]
        [:span.bookmark_link-icon-rating {:aria-hidden "true" :key (str id "-rating" i "-key")}]))
    (when show-created [:dfn " Created: " (str created)])
    (when show-last-visited [:dfn " Last Visited: " (str last-visited)])
    (when show-visits [:span.label.label-primary visits])
    (when (and show-new created (> (.getTime (parse-date created)) new-ticks))
      [:span.bookmark-new])
    (when (and show-visited last-visited (> (.getTime (parse-date last-visited)) last-visited-ticks))
      [:span.bookmark-visited])
    ]))

(defn search-page "Render the results of a bookmark search."
  []
  [:div.col-sm-12
   [header/header]
   [:div.breadcrumbs "Search: \"" (session/get :search) "\"" ]
   (doall (map #(bookmark-link (session/get %))
               (search-bookmarks (session/get :search)
                                 (:bookmark/children (session/get 1)))))
   [:div [:a {:href (path "/")} "go to the home page"]]])
