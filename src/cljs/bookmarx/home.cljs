(ns bookmarx.home
  (:require [reagent.session :as session]
            [goog.window :as gwin]
            [bookmarx.common :refer [env set-active parse-date]]
            [bookmarx.header :as header]))

(defn -get-route "Gets the route to a menu in a tree by id."
  ([id] (-get-route id [id]))
  ([id route]
   (if-let [parent (:bookmark/parent (session/get id))]
     (recur (:bookmark/id parent) (cons (:bookmark/id parent) route))
     route)))

(defn breadcrumb "Renders a breadcrumb."
  [id title active?]
  (if active?
    [:li.active {:key (str id "-bc-key")} (if (= title "~Trash") "Trash" title)]
    [:li {:key (str id "-bc-key")}
     [:a {:on-click #(set-active id) :key (str id "-a-key")}
      (if (= title "~Trash") "Trash" title)]]))

(defn breadcrumbs "Render breadcrumbs for a bookmark."
  []
  (let [route (-get-route (session/get :active))]
    [:ol.breadcrumbs
     (doall (map #(let [{:keys [bookmark/id bookmark/title]} (session/get %)]
                   (breadcrumb id title (= % (last route)))) route))]))

(defn count-links "Count the number of links in a folder and its children."
  ([children] (count-links children 0))
  ([children link-count]
   (cond
     (empty? children) link-count
     (session/get (:bookmark/id (first children)))
     (count-links (rest children) 
      (count-links (:bookmark/children (session/get (:bookmark/id (first children)))) link-count))
     :else (count-links (rest children) (inc link-count)))))

(defn bookmark-tree "Render a bookmark in a tree."
  [bookmark]
  (let [{:keys [bookmark/id bookmark/title bookmark/url bookmark/rating bookmark/children
                bookmark/icon bookmark/icon-color bookmark/created bookmark/last-visited
                bookmark/visits]} bookmark
        week-ago-ticks (- (. js/Date (now)) 604800000)]
    (if url
      [:div.bookmark_children {:key (str id "-key")}
       (if icon
         [:a {:class (str "glyphicon " icon) :aria-hidden "true"
              :style {:color (if icon-color icon-color "Black") :width "19px"}
             :key (str id "-icon-key") :on-click #(session/put! :add bookmark)
             :href (str (:prefix env) "/add")}]
         [:a.bookmark_link-icon {:aria-hidden "true" :key (str id "-icon-key")
                                 :on-click #(session/put! :add bookmark)
                                 :href (str (:prefix env) "/add")}])
       [:a.bookmark {:on-click #(gwin/open url) :key (str id "-link-key")} title]
       (when rating
         (for [i (range 0 rating)]
           [:span.bookmark_link-icon-rating {:aria-hidden "true"
                                             :key (str id "-rating" i "-key")}]))
       (when (and created (> (.getTime (parse-date created)) week-ago-ticks))
         [:span.bookmark-new])
       (when (and last-visited (> (.getTime (parse-date last-visited)) week-ago-ticks))
         [:span.bookmark-visited])]
      (let [{:keys [bookmark/children bookmark/title open?]} (session/get id)]
        [:div.bookmark_children {:key (str id "-key")}
         [:span {:class (str "bookmark_arrow" (when (not open?) "-collapsed"))
                 :key (str id "-arrow-key")
                 :on-click #(session/update-in! [id :open?] (fn [_] (not open?)))}]
         (if (= title "~Trash")
           [:span {:class "glyphicon glyphicon-trash bookmark-link" :key "~trash-icon-key"
                   :aria-hidden "true" :style {:width "19px"}}]
           [:a {:class (str "bookmark_folder-icon-" (if open? "open" "close"))
                :aria-hidden "true" :key (str id "-icon-key") :href (str (:prefix env) "/add")
                :on-click #(session/put! :add (assoc bookmark :folder? true))}])
         [:a.bookmark {:key (str id "-title-key") :on-click #(set-active id)}
          (if (= title "~Trash") "Trash" title)]
         [:span.badge (count-links children)]
         (when open? [:ul.nav.nav-pills.nav-stacked {:key (str id "-children-key")}
                      (doall (map #(bookmark-tree %) children))])]))))

(defn home-page "Render the Home page."
  []
  [:div.col-sm-12
   [header/header true]
   [breadcrumbs]
   (doall (map #(bookmark-tree %) (session/get-in [(session/get :active) :bookmark/children])))])
