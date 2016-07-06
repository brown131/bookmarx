(ns bookmarx.add
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [accountant.core :as accountant]
            [goog.string :as gstr]
            [goog.window :as gwin]
            [cljs-http.client :as http]))

(defn header "Render the header for the page."
  []
  [:span
   [:nav {:class "header-nav"}
    [:div {:class "container-fluid"}
     [:span {:class "header-navbar"} "Bookmarx"]
     [:span {:class "header-navbar header-star"}]]]])

(defn add "Add or edit a bookmark."
  []
  (let [{:keys [db/id bookmark/name bookmark/url bookmark/rating bookmark/_parent]}
        (session/get :add)
        folder? false
        rating0 true rating1 false rating2 false rating3 false rating4 false rating5 false]
    (header)
    [:fieldset {:class "form-group col-sm-6"}
     (when-not id
       [:div
        [:label {:for "folder-key"} "Folder? "]
        [:input {:key "folder-key" :type :checkbox :value folder?}]])
    [:label {:for "name-key"} "Name"]
    [:input {:class "form-control" :value name :placeholder "Enter name" :key "name-key"}]
    (when (or url (not folder?))
      [:div
        [:label {:for "url-key"} "URL"]
          [:input {:class "form-control" :value url :placeholder "Enter URL" :key "url-key"}]
          [:label "Rating" (gstr/unescapeEntities "&nbsp;") (gstr/unescapeEntities "&nbsp;")
           [:div {:class "radio-inline"}
            [:label [:input {:type "radio" :name "rating-opts" :value rating0 :key "rating0-key"}]
             "None"]]
           [:div {:class "radio-inline"}
            [:label [:input {:type "radio" :name "rating-opts" :value rating1 :key "rating1-key"}]
             "1"]]
           [:div {:class "radio-inline"}
            [:label [:input {:type "radio" :name "rating-opts" :value rating2 :key "rating2-key"}]
             "2"]]
           [:div {:class "radio-inline"}
            [:label [:input {:type "radio" :name "rating-opts" :value rating3 :key "rating3-key"}]
             "3"]]
           [:div {:class "radio-inline"}
            [:label [:input {:type "radio" :name "rating-opts" :value rating4 :key "rating4-key"}]
             "4"]]
           [:div {:class "radio-inline"}
            [:label [:input {:type "radio" :name "rating-opts" :value rating5 :key "rating5-key"}]
             "5"]]]])
     [:div [:button {:class "button" :type :button
                     :on-click #(accountant/navigate! "/")} "Add"]]]))