(ns bookmarx.login
  (:require [cljs.core.async :refer [<!]]
            [clojure.string :as s]
            [reagent.core :refer [atom]]
            [reagent.session :as session]
            [reagent-forms.core :refer [bind-fields]]
            [accountant.core :as accountant]
            [cemerick.url :refer [url]]
            [cljs.reader :refer [read-string]]
            [cljs-http.client :as http]
            [bookmarx.client :as client]
            [bookmarx.common :refer [path server-path set-cookie!]]
            [bookmarx.header :as header])
  (:require-macros
    [bookmarx.env :refer [cljs-env]]
    [cljs.core.async.macros :refer [go]]))

(defn row
  [label input]
  [:div.row
   [:div.col-sm-2 ^{:key label} [:label label]]
   [:div.col-sm-5 ^{:key input} input]])

(defn form-template
  [doc]
  [:div
    [:div {:field :container}
     [row "User" [:input.form-control {:field :text :id :user}]]
     [row "Password" [:input.form-control {:field :password :id :password}]]]])

(defn login [doc]
  (go (let [response (<! (http/post (server-path "/login")
                                    {:edn-params @doc
                                     :with-credentials? false
                                     :headers {"x-csrf-token" (session/get :csrf-token)}}))
            login-response (read-string (:body response))]
        (if (:error login-response)
          (swap! doc #(assoc % :error (:error login-response)))
          (let [auth-token (:auth-token login-response)
                redirect (path (or (get (:query (url (-> js/window .-location .-href))) "m") "/"))]
            (set-cookie! :auth-token auth-token (* (cljs-env :auth-token-hours) 60 60))
            (client/load-bookmarks)
            (client/get-settings)
            (accountant/navigate! redirect))))))

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
