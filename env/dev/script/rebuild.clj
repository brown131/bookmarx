#!/bin/bash lein-exec

;;;; Rebuild link counts in the Redis datastore.
(ns bookmarx.rebuild)

;; Load dependencies.
(use '[leiningen.exec :only (deps)])
(deps '[[com.taoensso/carmine "2.15.0"]])

;; Require packages.
(require '[taoensso.carmine :as car])

;; Setup redis connection.
(defonce bookmarx-conn {:pool {} :spec {:host "www.browncross.com"}})
(defmacro wcar* [& body] `(car/wcar bookmarx-conn (car/select 1) ~@body))

;; Load all bookmarks
(def bookmarks (atom (let [keys (remove symbol? (map read-string (second (wcar* (car/keys "*")))))
                           values (second (wcar* (apply car/mget keys)))]
                       (zipmap keys values))))

(defn count-children [folder]
  (reduce (fn [a b] (if (:bookmark/children (get @bookmarks b))
                      (+ a (count-children (get @bookmarks b)))
                      (inc a))) 0 (:bookmark/children folder)))

(wcar*
  (doseq [[id bookmark] @bookmarks]
    (when (:bookmark/children bookmark)
      (reset! bookmarks
              (update-in @bookmarks [id :bookmark/link-count] (fn [_] (count-children bookmark))))
      (when (not= (get-in @bookmarks [id :bookmark/link-count]) (:bookmark/link-count bookmark))
        (car/set id (get @bookmarks id))
        (println id (get-in @bookmarks [id :bookmark/link-count]) (:bookmark/link-count bookmark))))))
