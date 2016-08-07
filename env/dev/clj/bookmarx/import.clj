;;;; Import bookmarks from Bookmark4U DB into Bookmarx DB.
;;;; Run in bookmarx/svc with: lein exec script/import.clj
(ns import)

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

;;; Bookmark4U connection.
(def db-spec {:classname "com.mysql.jdbc.Driver" ; must be in classpath
              :subprotocol     "mysql"
              :subname         "//localhost:3306/bookmark4u"
              :user            "root"
              :password        "wurzel"})

;;; Recreate Bookmarx database.
(def uri "datomic:sql://bookmarx?jdbc:mysql://localhost:3306/datomic?user=datomic&password=datomic")
(delete-database uri)
(create-database uri)
(def schema-tx (read-string (slurp "resources/data/schema.edn")))
(def conn (connect uri))
@(transact conn schema-tx)

;;; Map Bookmark4U folder ids to Datomic UIDs.
(def uids (into {0 (squuid)}
                (mapv #(vector % (squuid))
                      (j/query db-spec ["select id from bookmark where url = ''"] {:row-fn :id}))))
(println "uids" (str uids))

;;; Import root folder.
(let [tx [{:db/id #db/id[:db.part/user] :bookmark/id (get uids 0) :bookmark/title "Root"}]]
  (println (str tx))
  @(transact conn tx))

;;; Import folders.
(doseq [rs (j/query db-spec ["select id, title from bookmark where url = ''"])]
  (let [tx [{:db/id #db/id[:db.part/user] :bookmark/id (get uids (:id rs))
             :bookmark/title (:title rs)}]]
    (println (str tx))
    @(transact conn tx)))

;;; Map Datomic folder UIDs to Datomic db/ids.
(def dbids (into {} (mapv #(vector (:bookmark/id (first %)) (:db/id (first %)))
                          (q '[:find (pull ?e [:bookmark/id :db/id])
                               :where [?e :bookmark/id]
                               [(missing? $ ?e :bookmark/url)]] (db conn)))))
(println "dbids" (str dbids))

;;; Set parent of folders.
(doseq [rs (j/query db-spec ["select id, parent from bookmark where url = ''"])]
  (let [tx [{:db/id (get dbids (get uids (:id rs)))
             :bookmark/parent (get dbids (get uids (:parent rs)))}]]
    (println (str tx))
    @(transact conn tx)))

(def icon-map {"beer.gif"      ["glyphicon-glass" "Orange"]
               "bludiamd.gif"  ["gylphicon-certificate" "Blue"]
               "bluered.gif"   ["glyphicon-adjust" "Blue"]
               "blusqare.gif"  ["glyphicon-stop" "Blue"]
               "con-blue.gif"  ["glyphicon-bookmark" "Blue"]
               "con-cyan.gif"  ["glyphicon-bookmark" "Cyan"]
               "con-green.gif" ["glyphicon-bookmark" "Green"]
               "con-oran.gif"  ["glyphicon-bookmark" "Orange"]
               "con-red.gif"   ["glyphicon-bookmark" "Red"]
               "de.gif"        ["glyphicon-flag" "Red"]
               "die4.gif"      ["glyphicon-th-large"]
               "grnsqare.gif"  ["glyphicon-stop" "Green"]
               "lock.gif"      ["glyphicon-lock"]
               "orgstar.gif"   ["glyphicon-star"]
               "redball.gif"   ["glyphicon-record" "Red"]
               "reddiamd.gif"  ["gylphicon-certificate" "Red"]
               "sun.gif"       ["glyphicon-sunlight" "Yellow"]
               "whtpearl.gif"  ["glyphicon-record-empty"]
               "ylwsqare.gif"  ["glyphicon-stop" "Yellow"]})

;;; Import links.
(doseq [rs (j/query db-spec ["select id, title, url, rate, parent, visit, rdate, lastvisit, icon from bookmark where url <> ''"])]
  (let [tx1 {:db/id #db/id[:db.part/user] :bookmark/id (squuid) :bookmark/title (:title rs)
             :bookmark/url (:url rs) :bookmark/rating (:rate rs)
             :bookmark/parent (get dbids (get uids (:parent rs)))
             :bookmark/visits (:visit rs) :bookmark/created (:rdate rs)
             :bookmark/last-visited (:lastvisit rs)}
        tx2 (if (first (get icon-map (:icon rs)))
              (assoc tx1 :bookmark/icon (first (get icon-map (:icon rs))))
              tx1)
        tx3 (if (second (get icon-map (:icon rs)))
              (assoc tx2 :bookmark/icon-color (second (get icon-map (:icon rs))))
              tx2)
        tx [(if (> (:rate rs) 0) (assoc tx1 :bookmark/rating (:rate rs)) tx3)]]
    (println (str tx))
    @(transact conn tx)))
