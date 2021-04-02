(ns bookmarx.handler
  (:require [clojure.set :refer [difference]]
            [clojure.string :as str]
            [taoensso.timbre :as t]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [ring.util.response :as r]
            [bookmarx.auth :as auth]
            [bookmarx.ds :as ds :refer [bookmarks]])
  (:import [java.util Date]))

(t/refer-timbre)

(def trash-id -1)

(defn build-response "Build a response with the changed bookmarks."
  [changed-ids]
  (let [latest-revision (ds/get-latest-revision)]
    ;; Return the list of changed bookmarks and the latest revision.
    {:bookmarks (doall (mapv #(get @bookmarks %) changed-ids)) :revision latest-revision}))

(defn sort-folder-children "Sort the children of a folder by a sort function."
  [folder]
  (let [[links folders] (map vec ((juxt filter remove) #(:bookmark/url (get @bookmarks %))
                                   (:bookmark/children folder)))
        make-sort-key #(cond
                         (= (:bookmark/id (get @bookmarks %)) trash-id) "~~~TRASH"
                         (:bookmark/title (get @bookmarks %))
                         (str/upper-case (:bookmark/title (get @bookmarks %)))
                         :else (do (t/warn (str "NO TITLE" % (str (get @bookmarks %)))) ""))]
    (update folder :bookmark/children
            (fn [_] (into [] (concat (if (empty? folders) folders (sort-by make-sort-key folders))
                                     (if (empty? links) links (sort-by make-sort-key links))))))))

(defn set-csrf-token "Add an anti-forgery token to an HTTP response."
  [response]
  (r/header response "csrf-token" *anti-forgery-token*))

(defn post-login "Authenticate the login page form."
  [{:keys [:user :password]}]
  (if-let [auth-token (auth/create-auth-token user password)]
    {:status 201 :body (pr-str {:auth-token auth-token})}
    {:status 401 :body (pr-str {:error "Invalid credentials."})}))

(defn get-bookmarks-since "Get bookmarks greater than a revision number in an HTTP request."
  [rev]
  (try
    (t/infof "get-bookmarks-since %s" rev)
    (let [rev-num (when-not (empty? rev) (read-string rev))
          rev-num (if (integer? rev-num) rev-num 0)
          changed-bookmarks
          (into [] (vals (remove #(or (keyword? (key %))
                                      (<= (:bookmark/revision (val %)) rev-num)) @bookmarks)))
          latest-revision (ds/get-latest-revision)]
      {:status 200
       :headers {"content-type" "application/edn"}
       :body {:bookmarks changed-bookmarks :revision latest-revision}})
      (catch Exception e (t/errorf "Error %s" (.toString e)))))

(defn post-bookmark "Add a bookmark into the database for an HTTP request."
  [{:keys [:bookmark/url :bookmark/parent-id] :as bookmark}]
  (try
    (t/infof "post-bookmark %s" (pr-str bookmark))
    (let [bookmark-id (ds/inc-last-bookmark-id!)
          now (Date.)
          new-bookmark (assoc (if url bookmark (assoc bookmark :bookmark/children []
                                                               :bookmark/link-count 0))
                         :bookmark/id bookmark-id :bookmark/created now :bookmark/last-visited now
                         :bookmark/visits 1 :bookmark/revision 1)
          changed-ids
          (loop [ancestor-ids [parent-id bookmark-id]]
            (let [ancestor (get @bookmarks (first ancestor-ids))]
              (if-not (:bookmark/parent-id ancestor)
                (reverse ancestor-ids)
                (recur (cons (:bookmark/parent-id ancestor) ancestor-ids)))))]
      ;; Increment the link count in the ancestors.
      (when url
        (dorun (map #(swap! bookmarks update-in [% :bookmark/link-count] inc) (rest changed-ids))))

      ;; Add the new bookmark to cache.
      (swap! bookmarks assoc bookmark-id new-bookmark)

      ;; Add the bookmark to its parent folder.
      (swap! bookmarks update-in [parent-id :bookmark/children] #(conj % bookmark-id))

      ;; Re-sort the bookmarks in the parent.
      (swap! bookmarks update parent-id sort-folder-children)

      ;; Persist the changes.
      (ds/save-bookmarks! changed-ids)

      {:status 201
       :headers {"content-type" "application/edn"}
       :body (build-response changed-ids)})
    (catch Exception e (t/errorf "Error %s" (.toString e)))))

(defn update-bookmark "Update a bookmark."
  [{:keys [:bookmark/id :bookmark/url :bookmark/title :bookmark/parent-id] :as bookmark} orig-bookmark]
  ;; Change the fields in the bookmark.
  (swap! bookmarks update id
         #(merge % (if url (select-keys bookmark [:bookmark/title :bookmark/url :bookmark/rating
                                                  :bookmark/icon :bookmark/icon-color])
                           (select-keys bookmark [:bookmark/title]))))

  ;; Return a list with the id of the changed bookmark, and the parent if the title changed.
  (if (= title (:bookmark/title orig-bookmark)) [id] [id parent-id]))

(defn move-bookmark "Move a bookmark to a different folder."
  [{:keys [:bookmark/id :bookmark/parent-id :bookmark/url] :as bookmark}]
  (let [orig-parent-id (:bookmark/parent-id (get @bookmarks id))
        ancestor-ids  ; Find the ancestors of the new parent.
        (loop [ancestor-ids [parent-id id]]
          (let [ancestor (get @bookmarks (first ancestor-ids))]
            (if-not (:bookmark/parent-id ancestor)
              (reverse ancestor-ids)
              (recur (cons (:bookmark/parent-id ancestor) ancestor-ids)))))
        orig-ancestor-ids  ; Find the ancestors of the original parent.
        (loop [ancestor-ids [orig-parent-id]]
          (let [ancestor (get @bookmarks (first ancestor-ids))]
            (if-not (:bookmark/parent-id ancestor)
              (reverse ancestor-ids)
              (recur (cons (:bookmark/parent-id ancestor) ancestor-ids)))))
        diffs1 (difference (set ancestor-ids) (set orig-ancestor-ids))
        diffs2 (difference (set orig-ancestor-ids) (set ancestor-ids))
        ;; Merge the differences between the change lists ensuring that both parents are kept.
        changed-ids (doall (distinct (concat diffs1 diffs2 [parent-id orig-parent-id])))]
    ;; Remove the bookmark from the original parent.
    (swap! bookmarks update-in [orig-parent-id :bookmark/children]
           #(into [] (remove (fn [b] (=  b id)) %)))

    ;; Add the bookmark to the new parent.
    (swap! bookmarks update-in [parent-id :bookmark/children] #(conj % id))

    ;; Change the fields in the bookmark.
    (swap! bookmarks update id
           #(merge % (if url
                       (select-keys bookmark [:bookmark/parent-id :bookmark/title :bookmark/url
                                              :bookmark/rating :bookmark/icon :bookmark/icon-color])
                       (select-keys bookmark [:bookmark/parent-id :bookmark/title]))))

    ;; Add the link count to the ancestors.
    (dorun (map #(when (get-in @bookmarks [% :bookmark/link-count])
                   (swap! bookmarks update-in [% :bookmark/link-count]
                          (fn [b] (if (:bookmark/link-count bookmark)
                                    (+ b (:bookmark/link-count bookmark))
                                    (inc b))))) (into [] diffs1)))

    ;; Subtract the link count from the original ancestors.
    (dorun (map #(when (get-in @bookmarks [% :bookmark/link-count])
                   (swap! bookmarks update-in [% :bookmark/link-count]
                          (fn [b] (if (:bookmark/link-count bookmark)
                                    (- b (:bookmark/link-count bookmark))
                                    (dec b))))) (into [] diffs2)))

    ;; Return the list of changed bookmark ids.
    changed-ids))

(defn put-bookmark "Update a bookmark in the database for an HTTP request."
  [id {:keys [:bookmark/title :bookmark/parent-id] :as bookmark}]
  (try
    (t/infof "put-bookmark %s %s" id (pr-str bookmark))
    (let [bookmark-id (Integer/parseInt id)
          orig-bookmark (get @bookmarks bookmark-id)
          changed-ids (if (= parent-id (:bookmark/parent-id orig-bookmark))
                        (update-bookmark bookmark orig-bookmark)
                        (move-bookmark bookmark))]
      ;; Re-sort the bookmarks in the parent if the title changed.
      (when-not (= title (:bookmark/title orig-bookmark))
        (swap! bookmarks update parent-id sort-folder-children))

      ;; Persist the changes.
      (ds/save-bookmarks! changed-ids)

      ;; Return the list of changed bookmarks.
      {:status 201
       :headers {"content-type" "application/edn"}
       :body (build-response changed-ids)})
    (catch Exception e (t/errorf "Error %s" (.toString e)))))

(defn put-bookmark-visit "Update visit information of a bookmark in the database for an HTTP request."
  [id]
  (try
    (t/infof "put-bookmark-visit %s" id)
    (let [bookmark-id (Integer/parseInt id)]
      ;; Update the last visited and number of visits.
      (swap! bookmarks assoc-in [bookmark-id :bookmark/last-visited] (Date.))
      (if (get-in @bookmarks [bookmark-id :bookmark/visits])
        (swap! bookmarks update-in [bookmark-id :bookmark/visits] inc)
        (swap! bookmarks assoc-in [bookmark-id :bookmark/visits] 1))

      ;; Persist the changes.
      (ds/update-bookmarks! [bookmark-id])

      ;; Return the list of changed bookmarks.
      {:status 201
       :headers {"content-type" "application/edn"}
       :body (build-response [bookmark-id])})
    (catch Exception e (t/errorf "Error %s" (.toString e)))))

(defn delete-bookmark "Delete a bookmark in the database."
  [id]
  (try
    (t/infof "delete-bookmark %s" id)
    (let [bookmark-id (Integer/parseInt id)
          {:keys [:bookmark/parent-id :bookmark/link-count]} (get @bookmarks bookmark-id)
          changed-ids
          (loop [ancestor-ids [parent-id]]
            (let [ancestor (get @bookmarks (first ancestor-ids))]
              (if-not (:bookmark/parent-id ancestor)
                (reverse ancestor-ids)
                (recur (cons (:bookmark/parent-id ancestor) ancestor-ids)))))
          deleted-ids
          ;; Create a list with the bookmark and its progeny.
          (doall (tree-seq #(:bookmark/children (get @bookmarks %))
                           #(:bookmark/children (get @bookmarks %)) bookmark-id))]
      ;; Remove deleted bookmarks from the cache.
      (swap! bookmarks #(apply dissoc % deleted-ids))

      ;; Remove the bookmark from its parent.
      (swap! bookmarks update-in [parent-id :bookmark/children]
             #(into [] (remove (fn [b] (= b bookmark-id)) %)))

      ;; Subtract the link count from the ancestors.
      (dorun (map #(when (:bookmark/link-count %)
                     (swap! bookmarks update-in [% :bookmark/link-count]
                            (fn [b] (if link-count (- b link-count) (dec b))))) changed-ids))

      ;; Persist the changes.
      (ds/save-bookmarks! changed-ids)

      ;; Delete the bookmark and its progeny.
      (ds/delete-bookmarks! deleted-ids)

      ;; Return the list of changed folders.
      {:status 200
       :headers {"content-type" "application/edn"}
       :body (assoc (build-response changed-ids) :deleted-ids (into [] deleted-ids))})
    (catch Exception e (t/errorf "Error %s" (.toString e)))))

(defn delete-trash "Delete bookmarks from the trash in the database."
  []
  (try
    (t/info "delete-trash")
    (let [{:keys [:bookmark/parent-id]} (get @bookmarks trash-id)
          changed-ids [parent-id trash-id]
          deleted-ids (get-in @bookmarks [trash-id :bookmark/children])]
      ;; Remove deleted bookmarks from the cache.
      (swap! bookmarks #(apply dissoc % deleted-ids))

      ;; Remove all bookmarks from the trash folder.
      (swap! bookmarks update-in [trash-id :bookmark/children] vector)

      ;; Subtract the link count from the ancestors.
      (swap! bookmarks update-in [trash-id :bookmark/link-count] (fn [_] 0))

      ;; Persist the changes.
      (ds/save-bookmarks! changed-ids)

      ;; Delete the bookmarks in the trash.
      (ds/delete-bookmarks! deleted-ids)

      ;; Return the list of changed folders.
      {:status 200
       :headers {"content-type" "application/edn"}
       :body (assoc (build-response changed-ids) :deleted-ids deleted-ids)})
    (catch Exception e (t/errorf "Error %s" (.toString e)))))

(defn get-settings "Get all settings and return them in an HTTP response."
  []
  (try
    (t/info "get-settings")
    {:status 200
     :headers {"content-type" "application/edn"}
     :body (ds/get-settings)}
    (catch Exception e (t/errorf "Error %s" (.toString e)))))

(defn post-settings "Add settings into the database for an HTTP request."
  [settings]
  (try
    (t/infof "post-settings %s" (pr-str settings))
    (ds/save-settings! settings)
    {:status 201
     :headers {"content-type" "application/edn"}}
    (catch Exception e (t/errorf "Error %s" (.toString e)))))
