(ns bookmarx.ds
  (:require [buddy.hashers :as hs]
            [config.core :refer [env]]
            [taoensso.carmine :as car]))

;; Setup redis connection.
(defonce bookmarx-conn {:pool {} :spec {:host "redis"}})
(def db (atom (env :database)))
(defmacro wcar* [& body] `(car/wcar bookmarx-conn (car/select @db) ~@body))

;; Bookmark cache
(def bookmarks (atom nil))

(defn cache-bookmarks []
  (reset! bookmarks (let [keys (remove symbol? (map read-string (second (wcar* (car/keys "*")))))
                          values (second (wcar* (apply car/mget keys)))]
                      (zipmap keys values))))

(defn get-latest-revision [] (second (wcar* (car/get "latest-revision"))))

(defn inc-latest-revision! [] (second (wcar* (car/incr "latest-revision"))))

(defn inc-last-bookmark-id! [] (second (wcar* (car/incr "last-bookmark-id"))))

(defn get-password "Gets the password hash." [] (second (wcar* (car/get "password"))))

(defn save-password! "Save the encrypted password."
  [password]
  (wcar*
    (car/set "password" (hs/derive password))
    (car/bgsave)))

(defn update-bookmarks! "Update changed bookmarks in the data store."
  [changed-ids]
  ; Update the revision.
  (wcar*
    (dorun (map #(car/set (key %) (val %)) (select-keys @bookmarks changed-ids)))
    (car/bgsave)))

(defn save-bookmarks! "Save changed bookmarks in the data store and update their revision."
  [changed-ids]
  ; Update the revision.
  (let [latest-revision (inc-latest-revision!)]
    ;; Save the revision in the changed bookmarks.
    (dorun (map #(swap! bookmarks update-in [% :bookmark/revision] (constantly latest-revision))
                changed-ids)))
  (update-bookmarks! changed-ids))

(defn delete-bookmarks! "Delete bookmarks from the data store."
  [deleted-ids]
  (wcar*
    (dorun (map car/del deleted-ids))
    (car/bgsave)))

(defn get-settings []
  (read-string (second (wcar* (car/get "settings")))))

(defn save-settings! "Save the settings in the data store."
  [settings]
  (wcar*
    (car/set "settings" (pr-str settings))
    (car/bgsave)))
