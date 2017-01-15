(ns bookmarx.login
  (:require [cljs.core.async :refer [<!]]
            [reagent.core :refer [atom]]
            [reagent.session :as session]
            [reagent-forms.core :refer [bind-fields init-field value-of]]

            [taoensso.timbre :as log]
            [cemerick.url :refer [url]]
            [cljs-http.client :as http]
            [bookmarx.common :refer [path server-path]]
            [bookmarx.header :as header])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

(defn user-auth?
  [doc]
  (go (let [body (:body (<! (http/post (server-path "/api/auth-token")
                                       {:edn-params @doc
                                        :with-credentials? false
                                        :headers {"x-csrf-token" (session/get :csrf-token)}})))])))

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
   [:button.btn.btn-default {:on-click #(user-auth? doc)} "Save"]])

(defn login-page "Render the Login page."
  []
  [:div.col-sm-12
   [header/header]
   (let [doc (atom {:user "" :password ""})]
     [editor doc [bind-fields (form-template doc) doc]])
   ])