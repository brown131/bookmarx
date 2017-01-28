(ns bookmarx.login
  (:require [reagent.session :as session]
            [reagent-forms.core :refer [bind-fields]]
            [bookmarx.client :refer [login]]
            [bookmarx.header :as header]))

(defn row
  [label input]
  [:div.row
   [:div.col-sm-2 ^{:key label} [:label label]]
   [:div.col-sm-5 ^{:key input} input]])

(defn form-template
  [doc]
  [:div {:field :container}
   [row "User" [:input.form-control {:field :text :id :user}]]
   [row "Password" [:input.form-control {:field :password :id :password}]]])

(defn editor [doc & body]
  [:div body
   (when (:error @doc)
     [:div.alert.alert-danger (:error @doc)])
   [:button.btn.btn-default {:on-click #(login doc)} "Login"]])

(defn login-page "Render the Login page."
  []
  [:div.col-sm-12
   [header/header]
   (let [doc (atom {:user "" :password ""})]
     [editor doc [bind-fields (form-template doc) doc]])])
