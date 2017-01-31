(ns bookmarx.settings
  (:require [reagent.core :refer [atom]]
            [reagent.session :as session]
            [reagent-forms.core :refer [bind-fields]]
            [accountant.core :as accountant]
            [bookmarx.client :refer [update-settings]]
            [bookmarx.common :refer [settings path]]
            [bookmarx.header :as header]))

(defn checkbox [label id]
  [:div.col-sm-2
   ^{:key label} [:label
                  [:div.col-sm-1 [:input {:field :checkbox :id id}]] label]])

(defn radio [label name value]
  [:div.col-sm-2
   ^{:key label} [:label
                  [:div.col-sm-1 [:input {:field :radio :name name :value value}]] label]])

(defn form-template
  []
  [:div
   [:div {:field :container}
    [:div.row [:div.col-sm-2 ^{:key "show"} [:h3 "Show:"]]]
    [:div.row
     (checkbox "Title" :show-title)
     (checkbox "URL" :show-url)
     (checkbox "Rating" :show-rating)
     (checkbox "Created" :show-created)]
    [:div.row
     (checkbox "Last Visited" :show-last-visited)
     (checkbox "Visits" :show-visits)
     (checkbox "New" :show-new)
     (checkbox "Visited" :show-visited)]
    [:div.row [:div.col-sm-2 ^{:key "sort-on"} [:h3 "Sort On:"]]]
    [:div.row
     (radio "Title" :sort-on :title)
     (radio "URL" :sort-on :url)
     (radio "Rating" :sort-on :rating)
     (radio "Created" :sort-on :created)]
    [:div.row
     (radio "Last Visited" :sort-on :last-visited)
     (radio "Visits" :sort-on :visits)
     (radio "New" :sort-on :new)
     (radio "Visited" :sort-on :visited)]
    ;; Advanced: Password "New Hours" "Visited Hours"
    ]])

(defn save-settings []
  (update-settings)
  (accountant/navigate! (path "/")))

(defn editor [& body]
  [:div body
   (when (:error @settings)
     [:div.alert.alert-danger (:error @settings)])
   [:div.col-sm-2 [:button.btn.btn-default {:on-click save-settings} "Save"]]])

(defn settings-page "Render the Settings page."
  []
  [:div.col-sm-12
   [header/header]
   [editor [bind-fields (form-template) settings]]])
