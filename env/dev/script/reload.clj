#!/bin/bash lein-exec

;;;; Reload bookmarks from a downloaded bookmarks EDN file into Redis.
(ns bookmarx.reload)

;; Load dependencies.
(use '[leiningen.exec :only (deps)])
(deps '[[com.taoensso/carmine "2.15.0"]])

;; Require packages.
(require '[taoensso.carmine :as car])

;; Setup redis connection.
(def bookmarx-conn {:pool {} :spec {}})
(defmacro wcar* [& body] `(car/wcar bookmarx-conn ~@body))

(def bookmarks (read-string (slurp "resources/bookmarks.edn")))

(println "Reloading revision" (:revision bookmarks) "of bookmarks")
(wcar*
  (car/select 1)
  (car/flushdb)
  (car/set "last-bookmark-id" (apply max (map :bookmark/id (:bookmarks bookmarks))))
  (car/set "latest-revision" (:revision bookmarks))
  (dorun (map #(car/set (:bookmark/id %) %) (:bookmarks bookmarks)))
  (car/save))
(println "Reloaded" (count (:bookmarks bookmarks)) "bookmarks")
