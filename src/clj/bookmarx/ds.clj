(ns bookmarx.ds
  (:require [buddy.hashers :as hs]
            [config.core :refer [env]]
            [taoensso.carmine :as car]))

;; Setup redis connection.
(defonce bookmarx-conn {:pool {} :spec {}})
(def db (atom (env :database)))
(defmacro wcar* [& body] `(car/wcar bookmarx-conn (car/select @db) ~@body))

;; Cache
(def bookmarks (atom (let [keys (remove symbol? (map read-string (second (wcar* (car/keys "*")))))
                           values (second (wcar* (apply car/mget keys)))]
                       (zipmap keys values))))

(defn get-latest-revision [] (second (wcar* (car/get "latest-revision"))))

(defn inc-last-bookmark-id! [] (second (wcar* (car/incr "last-bookmark-id"))))

(defn get-password "Gets the password hash." [] (second (wcar* (car/get "password"))))

(defn save-password! "Save the encrypted password."
  [password]
  (wcar*
    (car/set "password" (hs/derive password))
    (car/bgsave)))

(defn save-bookmarks! "Save changed bookmarks in the data store."
  [changed-ids]
  ; Update the revision.
  (let [latest-revision (second (wcar* (car/incr "latest-revision")))]
    ;; Set the revision in the changed bookmarks.
    (dorun (map #(swap! bookmarks update-in [% :bookmark/revision] (constantly latest-revision))
                changed-ids)))

  ;; Save the changes.
  (wcar*
    (dorun (map #(car/set (key %) (val %)) (select-keys @bookmarks changed-ids)))
    (car/bgsave)))

(defn delete-bookmarks! "Delete bookmarks from the data store."
  [deleted-ids]
  ;; Delete the bookmark and its progeny.
  (wcar*
    (dorun (map car/del deleted-ids))
    (car/bgsave)))