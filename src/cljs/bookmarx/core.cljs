(ns bookmarx.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]
              [goog.window :as gwin]
              [cljs-http.client :as http]
              [cljs.core.async :refer [<!]])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

;; -------------------------
;; Components

(defn header []
  [:span
    [:nav {:class "header-nav"}
     [:div {:class "container-fluid"}
      [:span {:class "header-navbar"} "Bookmarx"]
      [:span {:class "header-navbar header-star"}]
      [:form {:class "navbar-form" :role "search"}
       [:span {:class "navbar-right"}
        [:input {:type "text" :placeholder "Search"}]
        [:button {:class "header-button"}
         [:span {:class "glyphicon glyphicon-search" :color "white"}]]
        [:button {:class "header-button dropdown-toggle" :type "button"
                  :data-toggle "dropdown" :aria-haspopup true :aria-expanded false
                  :key "dropdownMenu2"}
         [:span {:class "glyphicon glyphicon-menu-hamburger" :color "white"}]]
        [:div {:class "dropdown-menu" :aria-labelledby="dropdownMenu2"}
         [:button {:class "dropdown-item" :type "button"} "Action"]
         [:div {:class "dropdown"}
          [:button {:class "dropdown-toggle" :data-toggle "dropdown"}
           "Show" [:span {:class "caret"}]]
          [:ul {:class "dropdown-menu"}
           [:li
            [:span {:class "dropdown"}
             [:button {:class "header-button dropdown-toggle" :data-toggle "dropdown"}
              "Show" [:span {:class "caret"}]]
             [:ul {:class "dropdown-menu"}
              [:li [:a {:href "#"} "Action 1"]]
              [:li [:a {:href "#"} "Another Action 1"]]
              [:li [:a {:href "#"} "Separated link 1"]]]]]
           [:li [:a {:href "#"} "Sort"]]
           [:li [:a {:href "#"} "Bookmark Add"]]]]
         [:button {:class "dropdown-item" :type "button"} "Something else"]]]]]]])

(defn -get-route
  "Gets the route to a menu in a tree by id."
  ([id] (-get-route id [id]))
  ([id route]
   (if-let [parent (:bookmark/parent (session/get id))]
     (recur (:db/id parent) (cons (:db/id parent) route))
     route)))

(defn breadcrumb
  "Renders a breadcrumb."
  [id name active?]
  (if active?
    [:li {:class "active" :key (str id "-bc-key")} name]
    [:li {:key (str id "-bc-key")}
     [:a {:on-click #(session/put! :active id) :key (str id "-a-key")} name]]))

(defn breadcrumbs
  []
  (let [route (-get-route (session/get :active))]
    [:ol {:class "breadcrumbs"}
     (doall (map #(let [{:keys [db/id bookmark/name]} (session/get %)]
                   (breadcrumb id name (= % (last route)))) route))]))

(defn bookmark [mark]
  (let [{:keys [db/id bookmark/name bookmark/url bookmark/rating bookmark/_parent]} mark]
    (if url
      [:div {:class "bookmark_children" :key (str id "-key")}
        [:div {:class "bookmark_link-icon" :aria-hidden "true" :key (str id "-icon-key")}]
         [:a {:on-click #(gwin/open url) :class "bookmark_link" :key (str id "-link-key")} name]
         (when rating
           (take rating
                 (repeat [:span {:class "bookmark_link-icon-rating" :aria-hidden "true"
                                 :key (str id "-rating-key")}])))]
      (let [{:keys [bookmark/_parent open?]} (session/get id)]
        [:div {:class "bookmark_children" :key (str id "-key")}
         [:span {:class (str "bookmark_arrow" (when (not open?) "-collapsed"))
                 :key (str id "-arrow-key")
                 :on-click #(do (println id "open?" open?)
                                (if open? (session/update-in! [id :open?] (fn [_] (not open?)))
                                          (session/assoc-in! [id :open?] true)))}]
         [:span {:class (str "bookmark_menu-icon-" (if open? "open" "close"))
                 :aria-hidden "true" :key (str id "-icon-key")
                 :on-click #(println "add")}]
         [:span {:key (str id "-name-key") :on-click #(session/put! :active id)} name]
         (when open? [:ul {:class "nav nav-pills nav-stacked":key (str id "-children-key")}
                      (doall (map #(bookmark %) _parent))])]))))

;; -------------------------
;; Views

(defn home-page []
  [:div {:class "col-sm-12"}
   (header)
   (breadcrumbs)
   ;(str (:bookmark/_parent (session/get (session/get :active))))
   (doall (map #(bookmark %) (session/get-in [(session/get :active) :bookmark/_parent])))
   [:div [:a {:href "/about"} "go to about page"]]])

(defn about-page []
  [:div {:class "col-sm-12"}
   (header)
   [:h2 "About bookmarx"]
   [:div [:a {:href "/"} "go to the home page"]]])

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/about" []
  (session/put! :current-page #'about-page))

;; -------------------------
;; Initialize app

;; Define the default API URL.
(goog-define api-url "http://localhost:3000/api/")

(defn sort-folder-children
  "Sorts the children of a folder by a sort key."
  [folder sort-key]
  (let [[l f] (map vec ((juxt filter remove) #(:bookmark/url %) (:bookmark/_parent folder)))]
    (update-in folder [:bookmark/_parent]
               #(into [] (concat (sort-by sort-key f) (sort-by sort-key l))))))

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init!
  "Load the bookmarks from the database and set the state for the application."
  []
  (go (let [body (:body (<! (http/get "http://localhost:3000/api/bookmarks"
                                      {:with-credentials? false})))
            bookmarks (mapv #(sort-folder-children (apply merge %) :bookmark/name) body)
            active (:db/id (first (filter #(nil? (:bookmark/parent %)) bookmarks)))]
        (session/put! :active active)
        (doall (map #(session/put! (:db/id %) %) bookmarks))
        (accountant/configure-navigation!
          {:nav-handler (fn [path] (secretary/dispatch! path))
           :path-exists? (fn [path] (secretary/locate-route path))})
        (accountant/dispatch-current!)
        (mount-root))))
