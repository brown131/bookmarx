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
            [bookmarx.common :refer [path server-path get-cookie set-cookie!]]
            [bookmarx.header :as header])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

(defn get-bookmarks "Request bookmarks from the server and set local state."
  [rev]
  (go (let [url (server-path "/api/bookmarks/since/" rev)
            response (<! (http/get url {:query-params {:csrf-token true} :with-credentials? false}))
            bookmarks (into {} (map #(vector (:bookmark/id %) %) (get-in response [:body :bookmarks])))
            revision (get-in response [:body :revision])]
        ;; Set the state.
        (session/put! :csrf-token (url-decode (get-in response [:headers "csrf-token"])))
        (set-cookie! :revision revision (* (get-cookie :cache-refresh-hours) 60 60))

        ;; Set the bookmarks.
        (reset! session/state (merge @session/state bookmarks))
        (when-not (= rev revision)
          (.setItem (.-localStorage js/window) "bookmarks"
                    (pr-str (into {} (remove #(keyword? (key %)) @session/state))))))))

(defn get-csrf-token "Request an anti-forgery token from the server."
  []
  (go (let [url (server-path "/api/csrf-token")
            response (<! (http/get url {:query-params {:csrf-token true} :with-credentials? false}))]
        (session/put! :csrf-token (url-decode (get-in response [:headers "csrf-token"]))))))

(defn load-bookmarks "Get bookmarks from the server and set local state."
  []
  (if (cookies/get "auth-token")
    (let [rev (js/parseInt (cookies/get "revision" 0))]
      (when-not (zero? rev)
        (let [bookmarks (read-string (.getItem (.-localStorage js/window) "bookmarks"))]
          (session/put! :revision rev)
          (reset! session/state (merge @session/state bookmarks))))
      (get-bookmarks rev))
    (get-csrf-token)))

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
            (load-bookmarks)

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
