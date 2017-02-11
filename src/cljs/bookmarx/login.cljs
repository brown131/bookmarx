(ns bookmarx.login
  (:require [clojure.string :as str]
            [reagent.core :refer [atom]]
            [reagent.cookies :as cookies]
            [reagent.session :as session]
            [reagent-forms.core :refer [bind-fields]]
            [accountant.core :as accountant]
            [cemerick.url :refer [url url-decode]]
            [cljs.reader :refer [read-string]]
            [bookmarx.client :as client]
            [bookmarx.common :refer [path server-path get-cookie set-cookie!]]
            [bookmarx.header :as header]))

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
  (client/login doc)
  (let [auth-token (session/get :auth-token)]
   ; (println "auth-token" auth-token)
    (if (map? auth-token)
      (swap! doc #(assoc % :error auth-token))
      (let [redirect (get (:query (url (-> js/window .-location .-href))) "m")
            env-map (read-string (str/replace (url-decode (cookies/get "env")) #"\+" " "))]
        (reset! session/state (merge @session/state env-map))
        (set-cookie! :auth-token auth-token (* (get-cookie :auth-token-hours) 60 60))
        (client/load-bookmarks)
        (client/get-settings)
        (accountant/navigate! (path (if redirect redirect "/")))))))

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
