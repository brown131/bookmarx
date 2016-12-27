;;;; Transfer bookmarks from datomic to redis.
;;;; Run in bookmarx/svc with: lein exec script/transfer.clj
(ns bookmarx.transfer)

;; Load dependencies.
(use '[leiningen.exec :only (deps)])
(deps '[[mysql/mysql-connector-java "5.1.21"]])
(deps '[[com.datomic/datomic-pro "0.9.5350" :exclusions [joda-time]]]
      :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo" :creds :gpg}})
(deps '[[com.taoensso/carmine "2.15.0"]])

;; Require packages.
(require '[datomic.api :as d])
(require '[taoensso.carmine :as car])
(require '[clojure.string :as str])

;; Setup redis connection.
(def bookmarx-conn {:pool {} :spec {}})
(defmacro wcar* [& body] `(car/wcar bookmarx-conn ~@body))

;; Last bookmark id.
(def last-bookmark-id (atom 0))
(def bookmark-id-map (atom {}))

(defn promote-root "Make the root the first bookmark in the list."
  [bookmarks]
  (into [] (cons (first (remove :bookmark/parent bookmarks))
                 (filter :bookmark/parent bookmarks))))

(defn replace-_parent "Rename '_parent' keys to 'children'."
  [bookmarks]
  (mapv #(if (:bookmark/_parent %)
          (dissoc (assoc % :bookmark/children (:bookmark/_parent %)) :bookmark/_parent)
          %) bookmarks))

(defn normalize-children "Ensure that all folders have just a db id in the map."
  [bookmarks]
  (mapv #(update % :bookmark/children
                (fn [p] (map (fn [b] (if (:bookmark/url b) b {:db/id (:db/id b)})) p)))
       bookmarks))

(defn get-bookmark-id "Map db ids to bookmark ids."
  [bookmark]
  (if (get @bookmark-id-map (:db/id bookmark))
    (get @bookmark-id-map (:db/id bookmark))
    (do (swap! last-bookmark-id inc)
        (swap! bookmark-id-map #(assoc % (:db/id bookmark) @last-bookmark-id))
        @last-bookmark-id)))

(defn replace-bookmark-id "Remove the db id, and replace old bookmark ids."
  [bookmark]
  (assoc (dissoc bookmark :db/id :bookmark/id) :bookmark/id (get-bookmark-id bookmark)))

(defn replace-bookmark-ids "Replace bookmark ids in all bookmarks."
  [bookmarks]
  (mapv #(update (replace-bookmark-id %) :bookmark/children
                 (fn [c] (doall (mapv (fn [b] (if (:bookmark/url b) (replace-bookmark-id b)
                                                           (get-bookmark-id b))) c))))
       bookmarks))

(defn replace-parent "Replace the parent id with a bookmark id."
  [bookmark]
  (if (:bookmark/parent bookmark)
    (dissoc (assoc bookmark :bookmark/parent-id
                            (get @bookmark-id-map (get-in bookmark [:bookmark/parent :db/id])))
              :bookmark/parent)
    bookmark))

(defn replace-parents "Replace the parent id with a bookmark id in all bookmarks."
  [bookmarks]
  (mapv #(if (:bookmark/children %) (update % :bookmark/children
                                           (fn [c] (map (fn [b] (replace-parent b)) c))) %)
       (map replace-parent bookmarks)))

(defn add-revision "Add a revision number to all bookmarks."
  [bookmarks]
  (map #(if (:bookmark/url %) % (assoc % :bookmark/revision 1)) bookmarks))

(defn count-child-links [bookmark bookmark-map]
  (apply + (map #(if (:bookmark/url %) 1 (count-child-links (get bookmark-map %) bookmark-map))
                (:bookmark/children bookmark))))

(defn count-links "Count child links in a folder."
  [bookmarks]
  (let [bookmark-map (reduce #(assoc %1 (:bookmark/id %2) %2) {} bookmarks)]
    (map #(assoc % :bookmark/link-count (count-child-links % bookmark-map)) bookmarks)))

(defn sort-folder-children "Sort the children of a folder by a sort function."
  [folder bookmark-map]
  (let [[links folders] (map vec ((juxt filter remove) :bookmark/url (:bookmark/children folder)))]
    (update folder :bookmark/children
            (fn [_] (into [] (concat (sort-by #(str/upper-case (:bookmark/title (get bookmark-map %)))
                                              folders)
                                     (sort-by #(str/upper-case (:bookmark/title %)) links)))))))
(defn sort-children "Sort all child bookmarks in a folder."
  [bookmarks]
  (let [bookmark-map (zipmap (map #(if (map? %) (:bookmark/id %) %) bookmarks) bookmarks)]
    (mapv #(sort-folder-children % bookmark-map) bookmarks)))

(defn rename-trash-folder "Rename the old trash folder."
  [bookmarks]
  (mapv #(if (= (:bookmark/title %) "~Trash")
           (update % :bookmark/title (fn [_] "Trash")) %) bookmarks))

(defn add-trash-folder "Add a new trash folder."
  [bookmarks]
  (-> bookmarks
      (conj {:bookmark/id -1 :bookmark/title "~Trash" :bookmark/parent-id 1 :bookmark/children []
             :bookmark/link-count 0})
      (update-in [0 :bookmark/children] #(conj % -1))))

(let [conn (d/connect "datomic:sql://bookmarx?jdbc:mysql://localhost:3306/datomic?user=datomic&password=datomic")
      results (d/q '[:find (pull ?e [:db/id :bookmark/id :bookmark/title :bookmark/url :bookmark/rating
                                     :bookmark/icon :bookmark/icon-color :bookmark/created
                                     :bookmark/last-visited :bookmark/visits :bookmark/parent
                                     {:bookmark/_parent 1}])
                     :where [?e :bookmark/id]
                     [(missing? $ ?e :bookmark/url)]] (d/db conn))
      bookmarks (-> results
                    (flatten)
                    (promote-root)
                    (replace-_parent)
                    (normalize-children)
                    (replace-bookmark-ids)
                    (replace-parents)
                    (add-revision)
                    (count-links)
                    (sort-children)
                    (rename-trash-folder)
                    (add-trash-folder)
                    )]
  (println (count bookmarks) @last-bookmark-id)
  (println "Root" (:bookmark/id (first (remove :bookmark/parent-id bookmarks))))
  (wcar*
    (car/select 1)
    (car/flushdb)
    (car/set "last-bookmark-id" @last-bookmark-id)
    (car/set "latest-revision" 1)
    (dorun (map #(do
                   (println "Transferring" (:bookmark/id %) (:bookmark/title %))
                   (car/set (:bookmark/id %) %)) bookmarks))
    (car/save)))
