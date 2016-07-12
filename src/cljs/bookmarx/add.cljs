(ns bookmarx.add
  (:require [reagent.core :as reagent :refer [atom dom-node]]
            [reagent.session :as session]
            [reagent-forms.core :refer [bind-fields init-field value-of]]
            [accountant.core :as accountant]
            [goog.dom :as dom]
            [goog.string :as gstr]
            [goog.window :as gwin]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [bookmarx.header :as header]))

(defn row
  [label input]
  [:div.row
   [:div.col-md-2 ^{:key label} [:label label]]
   [:div.col-md-5 ^{:key input} input]])

(def form-template
  [:div
   [:div {:field :container :visible? #(:add? %)}
    [row "Folder?" [:input.form-control {:field :checkbox :id :folder?}]]]
   [row "Name" [:input.form-control {:field :text :id :bookmark/name}]]
   [:div {:field :container :visible? #(not (:folder? %))}
     [row "URL" [:input.form-control {:field :text :id :bookmark/url}]]
     [row "Rating" [:input.form-control {:field :text :id :bookmark/rating}]]
   ]])

(defn editor [doc & body]
  [:div body
   [:button.btn.btn-default {:on-click #(session/update! :add @doc)} "Save"]
    [:h3 "Document State"]
   @doc])

(defn add-page "Render the Add/Edit page."
  []
  [:div {:class "col-sm-12"}
   [header/header]
   (let [doc (if (session/get :add) (atom (session/get :add)) (atom {:add? true}))]
     [editor doc [bind-fields form-template doc]])])
