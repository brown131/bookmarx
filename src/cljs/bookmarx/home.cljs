(ns bookmarx.home
  (:require [reagent.session :as session]
            [goog.window :as gwin]
            [bookmarx.client :refer [visit-bookmark]]
            [bookmarx.common :refer [ticks-in-hour settings path set-cookie! parse-date get-route]]
            [bookmarx.header :as header])
  (:require-macros
    [bookmarx.env :refer [cljs-env]]))

(enable-console-print!)

(defn breadcrumb "Renders a breadcrumb."
  [id title active?]
  (if active?
    [:li.active {:key (str id "-bc-key")} title]
    [:li {:key (str id "-bc-key")}
     [:a {:on-click #(set-cookie! :active id) :key (str id "-a-key")} title]]))

(defn breadcrumbs "Render breadcrumbs for a bookmark."
  []
  (let [route (get-route (session/get :active 1))]
    [:ol.breadcrumbs
     (doall (map #(let [{:keys [bookmark/id bookmark/title]} (session/get %)]
                   (breadcrumb id title (= % (last route)))) route))]))

(defn link-click [id url]
  (visit-bookmark id)
  (gwin/open url))

(defn folder-open [id]
  ;(visit-bookmark id)
  (session/update-in! [id :open?] #(not %)))

(defn folder-click [id]
  (visit-bookmark id)
  (set-cookie! :active id))

(defn bookmark-link "Render a link."
  [{:keys [bookmark/id bookmark/title bookmark/url bookmark/rating
           bookmark/icon bookmark/icon-color bookmark/created bookmark/last-visited
           bookmark/visits] :as bookmark}]
  (let [{:keys [show-title show-url show-rating show-created show-last-visited show-visits show-new
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
     [:span.bookmark-visited])]))

(declare bookmark-tree)

(defn bookmark-folder "Render a folder."
  [{:keys [bookmark/id] :as bookmark}]
  (let [{:keys [bookmark/children bookmark/title bookmark/link-count open?]} (session/get id)]
    (when (and (not= id (session/get :active)) (or (not (empty? children)) (= id -1)))
      [:div.bookmark_children {:key (str id "-key")}
       [:span {:class (str "bookmark_arrow" (when (not open?) "-collapsed"))
               :key (str id "-arrow-key")
               :on-click #(folder-open id)}]
       (if (= id -1)
         [:span {:class "glyphicon glyphicon-trash bookmark-link" :key "trash-icon-key"
                 :aria-hidden "true" :style {:width "19px"}}]
         [:a {:class (str "bookmark_folder-icon-" (if open? "open" "close"))
              :aria-hidden "true" :key (str id "-icon-key") :href (path "/add")
              :on-click #(session/put! :add (assoc bookmark :folder? true))}])
       [:a.bookmark {:key (str id "-title-key") :on-click #(folder-click id)} title]
       [:span.badge link-count]
       (when open? [:ul.nav.nav-pills.nav-stacked {:key (str id "-children-key")}
                    (bookmark-tree children)])])))

(defn bookmark-tree "Render a bookmark in a tree."
  [children]
  (doall (map #(let [{:keys [bookmark/url] :as bookmark} (session/get %)]
                 (if url (bookmark-link bookmark) (bookmark-folder bookmark))) children)))

(defn home-page "Render the Home page."
  []
  [:div.col-sm-12
   [header/header true]
   [breadcrumbs]
   (bookmark-tree (session/get-in [(session/get :active 1) :bookmark/children]))])
