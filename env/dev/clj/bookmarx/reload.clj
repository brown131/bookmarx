;;;; Reload bookmarks from a bookmarks download file.
(ns bookmarx.reload
  (:gen-class))

;; Load dependencies.
(use '[leiningen.exec :only (deps)])
(deps '[[org.clojure/java.jdbc "0.6.1"]
        [mysql/mysql-connector-java "5.1.21"]])
(deps '[[com.datomic/datomic-pro "0.9.5350" :exclusions [joda-time]]]
      :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo" :creds :gpg}})

;; Require packages.
(require '[clojure.java.jdbc :as j])
(require '[datomic.api :refer [connect create-database db delete-database q squuid transact]])

(#'clojure.core/load-data-readers)
(set! *data-readers* (.getRawRoot #'*data-readers*))

;;; Recreate Bookmarx database.
(def uri "datomic:sql://bookmarx?jdbc:mysql://localhost:3306/datomic?user=datomic&password=datomic")
(delete-database uri)
(create-database uri)
(def schema-tx (read-string (slurp "resources/data/schema.edn")))
(def conn (connect uri))
@(transact conn schema-tx)

(def download (flatten (read-string (slurp "resources/data/bookmarks.edn"))))

(def root (first (filter #(nil? (:bookmark/parent %)) download)))

(def old-db-ids (reduce #(assoc %1 (:db/id %2) %2) {} download))

(def dbids (atom {}))

(def retry (atom []))

(def bookmark-ids (apply merge (map #(merge {(:bookmark/id %) %}
                                             (apply merge (map (fn [b] {(:bookmark/id b) b})
                                                              (:bookmark/_parent %)))) download)))
(defn get-bookmarks [bookmark]
  (vec (reduce #(if (:bookmark/url %2)
                  (conj %1 %2)
                  (concat %1 (get-bookmarks (get old-db-ids (:db/id %2)))))
               [bookmark] (:bookmark/_parent bookmark))))

(defn prepare-bookmark [bookmark]
  (-> bookmark
      (dissoc :bookmark/_parent)
      (update-in [:db/id] (fn [_] #db/id[:db.part/user]))
      (#(if (:bookmark/parent %)
          (update-in % [:bookmark/parent :db/id] (fn [b] (get @dbids b)))
          %))))

(defn load-bookmarks [bookmarks]
  (dorun (map #(if (or (not (:bookmark/parent %))
                       (get @dbids (get-in % [:bookmark/parent :db/id])))
                 (let [response @(transact conn [(prepare-bookmark %)])
                       dbid (second (first (vec (:tempids response))))]
                   (println "Reloading " (:db/id %))
                   (swap! dbids assoc (:db/id %) dbid))
                 (do (swap! retry conj %) nil)) bookmarks)))

(load-bookmarks (get-bookmarks root))

(while (not (empty? @retry))
  (let [bookmarks @retry]
    (reset! retry [])
    (load-bookmarks bookmarks)))
