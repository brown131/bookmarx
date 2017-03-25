(ns bookmarx.client
  (:require [cljs.core.async :refer [<!]]
            [reagent.session :as session]
            [taoensso.timbre :as log]
            [cemerick.url :refer [url-decode]]
            [cljs.reader :refer [read-string]]
            [cljs-http.client :as http]
            [bookmarx.common :refer [settings path server-path get-cookie set-cookie!]])
  (:require-macros
    [bookmarx.env :refer [cljs-env]]
    [cljs.core.async.macros :refer [go]]))

(defn get-edn-params "Remove temporary fields from the page document."
  [doc]
  (dissoc (if (:folder? @doc) (dissoc @doc :bookmark/url) @doc)
          :bookmark/children :orig-parent-id :folder? :add? :delete? :query? :rating :rating-clicked))

(defn add-bookmark "Add a new bookmark."
  [doc]
  (go (let [body (:body (<! (http/post (server-path "/api/bookmarks")
                                       {:edn-params (get-edn-params doc)
                                        :with-credentials? false
                                        :headers {"x-csrf-token" (session/get :csrf-token)}})))
            bookmark (first (if (:bookmark/url @doc)
                              (filter #(= (:bookmark/id %) (:bookmark-id body))
                                      (:bookmark/children (first (:bookmarks body))))
                              (:bookmarks body)))]
        ;; Set the new fields.
        (swap! doc merge (select-keys bookmark [:bookmark/id :bookmark/created :bookmark/last-visited
                                                :bookmark/visits :bookmark/revision]))

        ;; Replace the changed folders in the session.
        (dorun (map #(session/put! (:bookmark/id %) %) (:bookmarks body)))

        ;; Update the revision number in the session.
        (session/put! :revision (:revision body)))))

(defn update-bookmark "Update a bookmark."
  [doc]
  ;; Update the state in the remote repository.
  (go (let [body (:body (<! (http/put (server-path"/api/bookmarks/" (:bookmark/id @doc))
                                      {:edn-params (get-edn-params doc)
                                       :with-credentials? false
                                       :headers {"x-csrf-token" (session/get :csrf-token)}})))]
        ;; Replace changed bookmarks in the session.
        (dorun (map #(session/put! (:bookmark/id %) %) (:bookmarks body)))

        ;; Update the revision number in the session.
        (session/put! :revision (:revision body)))))

(defn visit-bookmark "Update a bookmark's visit information."
  [id]
  ;; Update visit state in the remote repository.
  (go (let [body (:body (<! (http/put (server-path "/api/bookmarks/visit/" id)
                                      {:with-credentials? false
                                       :headers {"x-csrf-token" (session/get :csrf-token)}})))]
        ;; Replace changed bookmarks in the session.
        (dorun (map #(session/put! (:bookmark/id %) %) (:bookmarks body))))))

(defn delete-bookmark "Delete the bookmark on the backend service."
  [doc]
  (log/debugf "delete")
  (go (let [body (:body (<! (http/delete (server-path "/api/bookmarks/"
                                                      (:bookmark/id @doc))
                                         {:edn-params (get-edn-params doc)
                                          :with-credentials? false
                                          :headers {"x-csrf-token" (session/get :csrf-token)}})))]
        ;; Remove the bookmark and its progeny from the session.
        (dorun (map session/remove! (:deleted-ids body)))

        ;; Replace the ancestor bookmarks in the session.
        (dorun (map #(session/put! (:bookmark/id %) %) (:bookmarks body)))

        ;; Update the revision number in the session.
        (session/put! :revision (:revision body)))))

(defn empty-trash "Empty trash on the backend service."
  []
  (log/debugf "empty trash")
  (go (let [body (:body (<! (http/delete (server-path "/api/bookmarks/trash")
                                         {:with-credentials? false
                                          :headers {"x-csrf-token" (session/get :csrf-token)}})))]
        ;; Remove the bookmark and its progeny from the session.
        (dorun (map session/remove! (:deleted-ids body)))

        ;; Replace the ancestor bookmarks in the session.
        (dorun (map #(session/put! (:bookmark/id %) %) (:bookmarks body)))

        ;; Update the revision number in the session.
        (session/put! :revision (:revision body)))))

(defn get-bookmarks "Request bookmarks from the server and set local state."
  [rev]
  (go (let [url (server-path "/api/bookmarks/since/" rev)
            response (<! (http/get url {:query-params {:csrf-token true} :with-credentials? false}))
            bookmarks (into {} (map #(vector (:bookmark/id %) %) (get-in response [:body :bookmarks])))
            revision (get-in response [:body :revision])]
        ;; Set the state.
        (session/put! :csrf-token (url-decode (get-in response [:headers "csrf-token"])))
        (set-cookie! :revision revision (* (cljs-env :cache-refresh-hours) 60 60))

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
  (if (get-cookie :auth-token)
    (let [rev (js/parseInt (get-cookie :revision 0))]
      (when-not (zero? rev)
        (let [bookmarks (read-string (.getItem (.-localStorage js/window) "bookmarks"))]
          (reset! session/state (merge @session/state bookmarks))))
      (get-bookmarks rev))
    (get-csrf-token)))

(defn get-settings "Request settings from the server and set session state."
  []
  (go (let [url (server-path "/api/settings")
            response (<! (http/get url {:with-credentials? false}))]
        (reset! settings (:body response)))))

(defn update-settings "Persist settings on the server."
  []
  (go (<! (http/post (server-path "/api/settings")
                     {:edn-params @settings
                      :with-credentials? false
                      :headers {"x-csrf-token" (session/get :csrf-token)}}))))

