(ns bookmarx.home
  (:require [reagent.session :as session]
            [goog.window :as gwin]
            [bookmarx.env :refer [env set-active]]
            [bookmarx.header :as header]))

(enable-console-print!)

(defn -get-route "Gets the route to a menu in a tree by id."
  ([id] (-get-route id [id]))
  ([id route]
   (if-let [parent (:bookmark/parent (session/get id))]
     (recur (:db/id parent) (cons (:db/id parent) route))
     route)))

(defn breadcrumb "Renders a breadcrumb."
  [id name active?]
  (if active?
    [:li.active {:key (str id "-bc-key")} name]
    [:li {:key (str id "-bc-key")}
     [:a {:on-click #(set-active id) :key (str id "-a-key")} name]]))

(defn breadcrumbs "Render breadcrumbs for a bookmark."
  []
  (let [route (-get-route (session/get :active))]
    [:ol.breadcrumbs
     (doall (map #(let [{:keys [db/id bookmark/name]} (session/get %)]
                   (breadcrumb id name (= % (last route)))) route))]))

(defn bookmark-tree "Render a bookmark in a tree."
  [mark]
  (let [{:keys [db/id bookmark/name bookmark/url bookmark/rating bookmark/_parent]} mark]
    (if url
      [:div.bookmark_children {:key (str id "-key")}
       [:a.bookmark_link-icon {:aria-hidden "true" :key (str id "-icon-key")
            :on-click #(session/put! :add mark) :href (str (:prefix env) "/add")}]
       [:a.bookmark {:on-click #(gwin/open url) :key (str id "-link-key")} name]
       (when rating
         (take rating (repeat [:span.bookmark_link-icon-rating 
                               {:aria-hidden "true" :key (str id "-rating-key")}])))]
      (let [{:keys [bookmark/_parent open?]} (session/get id)]
        [:div.bookmark_children {:key (str id "-key")}
         [:span {:class (str "bookmark_arrow" (when (not open?) "-collapsed"))
                 :key (str id "-arrow-key")
                 :on-click #(session/update-in! [id :open?] (fn [_] (not open?)))}]
         [:a {:class (str "bookmark_folder-icon-" (if open? "open" "close"))
              :aria-hidden "true" :key (str id "-icon-key") :href (str (:prefix env) "/add")
              :on-click #(session/put! :add (assoc mark :folder? true))}]
         [:a.bookmark {:key (str id "-name-key") :on-click #(set-active id)} name]
         (when open? [:ul.nav.nav-pills.nav-stacked {:key (str id "-children-key")}
                      (doall (map #(bookmark-tree %) _parent))])]))))

(defn home-page "Render the Home page."
  []
  [:div.col-sm-12
   [header/header true]
   [breadcrumbs]
   (doall (map #(bookmark-tree %) (session/get-in [(session/get :active) :bookmark/_parent])))])
