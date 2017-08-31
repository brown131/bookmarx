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

;; Load all bookmarks (zipmap keys values))))
(def bookmarks (atom (let [keys (remove symbol? (map read-string (second (wcar* (car/keys "*")))))
                           values (second (wcar* (apply car/mget keys)))]
                            (zipmap keys values))))
#_(dorun (map #(when (not (string? %)) (println %)) (second (wcar* (car/keys "*")))))

#_(dorun (map #(when (and % (vector? (read-string %))) (println %)) (second (wcar* (car/keys "*")))))

;;; Update link counts

#_(defn count-children [folder]
        (reduce (fn [a b] (if (:bookmark/children (get @bookmarks b))
                            (+ a (count-children (get @bookmarks b)))
                            (inc a))) 0 (:bookmark/children folder)))


#_(wcar*
    (doseq [[id bookmark] @bookmarks]
           (when (:bookmark/children bookmark)
                 (reset! bookmarks
                         (update-in @bookmarks [id :bookmark/link-count] (fn [_] (count-children bookmark))))
                 (when (not= (get-in @bookmarks [id :bookmark/link-count]) (:bookmark/link-count bookmark))
                       (car/set id (get @bookmarks id))
                       (println id (get-in @bookmarks [id :bookmark/link-count]) (:bookmark/link-count bookmark))))))

;;; Remove dead children

#_(doseq [[id bookmark] @bookmarks]
       (when (:bookmark/children bookmark)
             (let [children (vec (filter #(get @bookmarks %) (:bookmark/children bookmark)))]
                  (when-not (= (count children) (count (:bookmark/children bookmark)))
                            (reset! bookmarks (assoc-in @bookmarks [id :bookmark/children] children))
                            (wcar* (car/set id (get @bookmarks id)))
                            (println id children (:bookmark/children bookmark))))))

;;; Update parent ids

(defn reset-parent
      [id parent-id]
      (when (and parent-id (not= parent-id (get-in @bookmarks [id :bookmark/parent-id])))
            (println id (get-in @bookmarks [id :bookmark/parent-id]) parent-id)
            (reset! bookmarks (update-in @bookmarks [id :bookmark/parent-id] (fn [_] parent-id)))
              (car/set id (get @bookmarks id))))

(wcar*
    (doseq [[id bookmark] @bookmarks]
           (when (:bookmark/children bookmark)
                 (doall (map #(reset-parent % id) (:bookmark/children bookmark))))))

;;; Reset trash

#_(wcar* (car/set -1 {:bookmark/id -1, :bookmark/title "Trash", :bookmark/parent-id 1, :bookmark/children [],
                      :bookmark/link-count 0, :bookmark/revision 1}))


;(dorun (map println (filter #(= (:bookmark/title (second %)) "Machine Learning") @bookmarks)))