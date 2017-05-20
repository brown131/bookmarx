#!/bin/bash lein-exec

;;;; Find orphaned bookmarks in the Redis datastore.
(ns bookmarx.orphans)

;; Load dependencies.
(use '[leiningen.exec :only (deps)])
(deps '[[com.taoensso/carmine "2.15.0"]])

;; Require packages.
(require '[taoensso.carmine :as car])

;; Setup redis connection.
(defonce bookmarx-conn {:pool {} :spec {:host "www.browncross.com"}})
(defmacro wcar* [& body] `(car/wcar bookmarx-conn (car/select 1) ~@body))

;; Load all bookmarks
(def bookmarks (let [keys (remove symbol? (map read-string (second (wcar* (car/keys "*")))))
                     values (second (wcar* (apply car/mget keys)))]
                 (zipmap keys values)))

(println "Searching for orphaned bookmarks")
(dorun (map #(when-not (get bookmarks (:bookmark/parent-id (second %)))
               (println (second %)))
            (filter #(> (first %) 1) bookmarks)))
