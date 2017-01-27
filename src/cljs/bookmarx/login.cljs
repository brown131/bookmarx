(ns bookmarx.login
  (:require [cljs.core.async :refer [<!]]
            [reagent.core :refer [atom]]
            [reagent.cookies :as cookies]
            [reagent.session :as session]
            [reagent-forms.core :refer [bind-fields]]
            [accountant.core :as accountant]
            [cemerick.url :refer [url url-decode]]
            [cljs-http.client :as http]
            [cljs.reader :refer [read-string]]
            [bookmarx.common :refer [path server-path get-cookie set-cookie! load-bookmarks]]
            [bookmarx.header :as header])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

(defn login
  [doc]
  (go (let [results (<! (http/post (server-path "/login")
                                   {:edn-params @doc
                                    :with-credentials? false
                                    :headers {"x-csrf-token" (session/get :csrf-token)}}))
            auth-token (:auth-token (read-string (:body results)))]
        (if-not auth-token
          (swap! doc #(assoc % :error (:body results)))
          (let [redirect (get (:query (url (-> js/window .-location .-href))) "m")]
            ;; Save token.
            (set-cookie! :auth-token auth-token (* (get-cookie :auth-token-hours) 60 60))

            ;; Load bookmarks.
            (let [revision (js/parseInt (cookies/get "revision" 0))]
              (when-not (zero? revision)
                (reset! session/state
                        (merge @session/state
                               (read-string (.getItem (.-localStorage js/window) "bookmarks")))))
              (load-bookmarks revision))

            ;; Redirect to requested page.
            (accountant/navigate! (path (if redirect redirect "/"))))))))

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
