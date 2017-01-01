(ns bookmarx.add
  (:require [clojure.string :as str]
            [reagent.core :refer [atom]]
            [reagent.session :as session]
            [reagent-forms.core :refer [bind-fields init-field value-of]]
            [accountant.core :as accountant]
            [taoensso.timbre :as log]
            [cemerick.url :refer [url]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [bookmarx.common :refer [env get-active sort-folder-children]]
            [bookmarx.header :as header])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

(defn get-edn-params "Remove temporary fields from the page document."
  [doc]
  (dissoc (if (:folder? @doc) (dissoc @doc :bookmark/url) @doc)
          :bookmark/children :orig-parent-id :folder? :add? :delete? :query? :rating :rating-clicked))

(defn get-active-bookmark-id "Get the active bookmark id."
  [doc]
  (if (:bookmark/parent-id @doc)
    (:bookmark/parent-id @doc)
    (get-active)))

(defn add-bookmark "Add a new bookmark."
  [doc]
  (go (let [body (:body (<! (http/post (str (:host-url env) (:prefix env) "/api/bookmarks")
                                       {:edn-params (get-edn-params doc)
                                        :with-credentials? false
                                        :headers {"x-csrf-token" (session/get :csrf-token)}})))
            bookmark (first (if (:bookmark/url @doc)
                              (filter #(= (:bookmark/id %) (:bookmark-id body))
                                      (:bookmark/children (first (:bookmarks body))))
                              (:bookmarks body)))]
        ;; Set the new fields.
        (swap! doc merge (select-keys bookmark [:bookmark/id :bookmark/created :bookmark/last-visited
                                                :bookmark/visits :bookmark/revision]))

        ;; Replace the changed folders in the session.
        (dorun (map #(session/put! (:bookmark/id %) %) (:bookmarks body)))

        ;; Update the revision number in the session.
        (session/put! :revision (:revision body)))))

(defn update-bookmark "Update a bookmark."
  [doc]
    ;; Update the state in the remote repository.
    (go (let [body (:body (<! (http/put (str (:host-url env) (:prefix env) "/api/bookmarks/"
                                             (:bookmark/id @doc))
                                           {:edn-params (get-edn-params doc)
                                            :with-credentials? false
                                            :headers {"x-csrf-token" (session/get :csrf-token)}})))]
          ;; Replace the ancestor bookmarks in the session.
          (dorun (map #(session/put! (:bookmark/id %) %) (:bookmarks body)))

          ;; Update the revision number in the session.
          (session/put! :revision (:revision body)))))

(defn delete-bookmark "Delete the bookmark on the backend service."
  [doc]
  (log/debugf "delete")
  (go (let [body (:body (<! (http/delete (str (:host-url env) (:prefix env) "/api/bookmarks/"
                                              (:bookmark/id @doc))
                                         {:edn-params (get-edn-params doc)
                                          :with-credentials? false
                                          :headers {"x-csrf-token" (session/get :csrf-token)}})))]
        ;; Remove the bookmark and its progeny from the session.
        (dorun (map session/remove! (:deleted-ids body)))

        ;; Replace the ancestor bookmarks in the session.
        (dorun (map #(session/put! (:bookmark/id %) %) (:bookmarks body)))

        ;; Update the revision number in the session.
        (session/put! :revision (:revision body)))))

(defn trash-bookmark "Move a bookmark to the trash folder."
  [doc]
  (swap! doc update-in [:orig-parent-id] #(:bookmark/parent-id @doc))
  (swap! doc update-in [:bookmark/parent-id] #(- 0 1))
  (update-bookmark doc))
  
(defn save-bookmark "Save a bookmark."
  [doc]
  (log/debugf "save %s" @doc)
  (cond (:add? @doc) (add-bookmark doc)
        (:delete? @doc) (if (or (:folder? @doc) (= (:bookmark/parent-id @doc) -1))
                          (delete-bookmark doc)
                          (trash-bookmark doc))
        :else (update-bookmark doc))

  (accountant/navigate! (str (:prefix env) "/"))
  (when (:query? @doc)
    (.setTimeout js/window #(.close js/window) 1000)))

(defn rating-star "Renders a bookmark rating star."
  [index doc]
  [:span {:class (if (<= index (if (:rating-clicked @doc) (:bookmark/rating @doc) (:rating @doc)))
                   "glyphicon glyphicon-star"
                   "glyphicon glyphicon-star-empty")
          :key (str "rating-star-" index) :data-index index
          :on-mouse-enter #(do
                            (swap! doc update-in [:rating-clicked] (fn [] false))
                            (swap! doc update-in [:rating] (fn [] index)))
          :on-mouse-leave #(do
                             (swap! doc update-in [:rating] (fn [] 0))
                             (when-not (:rating-clicked @doc)
                               (swap! doc update-in [:bookmark/rating] (fn [] 0))))
          :on-click #(do
                      (swap! doc update-in [:rating-clicked] (fn [] true))
                      (swap! doc update-in [:bookmark/rating] (fn [] index)))}])

(defn rating-stars "Render stars for rating bookmarks."
  [doc]
  [:div (for [i (range 1 6)]
          [rating-star i doc])])

(defn folder-selector "Render parent folder selection."
  [doc]
  [:a.bookmark {:on-click #(do
                             (when-not (:orig-parent @doc)
                               (swap! doc update-in [:orig-parent-id] (fn [] (:bookmark/parent-id @doc))))
                             (if (session/get :add)
                               (session/update-in! [:add :orig-parent-id] (fn [] (:bookmark/parent-id @doc)))
                               (session/put! :add @doc)))
                :href (str (:prefix env) "/folder")}
   (let [title (:bookmark/title (session/get (get-active-bookmark-id doc)))]
     (if (= title "~Trash") "Trash" title))])

(defn icon-selector "Render icon selection."
  [doc]
  (let [{:keys [:bookmark/icon :bookmark/icon-color]} @doc] 
    [:a {:class (str "bookmark_link-icon glyphicon " icon) 
         :style {:color (if icon-color icon-color "Black")}
         :href (str (:prefix env) "/icon")}]))

(defn row
  [label input]
  [:div.row
   [:div.col-sm-2 ^{:key label} [:label label]]
   [:div.col-sm-5 ^{:key input} input]])

(defn form-template
  [doc]
  [:div
   [:div {:field :container :visible? #(and (:add? %) (not (:query? %)))}
    [row "Folder?" [:input.form-control {:field :checkbox :id :folder?}]]]
   [row "Title" [:input.form-control {:field :text :id :bookmark/title}]]
   [:div {:field :container :visible? #(not (:folder? %))}
    [row "URL" [:input.form-control {:field :text :id :bookmark/url}]]
    [row "Rating" [rating-stars doc]]
    [row "Icon" [icon-selector doc]]]
   [row "Parent Folder" [folder-selector doc]]
   [:div {:field :container :visible? #(not (:add? %))}
    [row "Delete?" [:input.form-control {:field :checkbox :id :delete?}]]]])

(defn editor [doc & body]
  [:div body
   [:button.btn.btn-default {:on-click #(save-bookmark doc)} "Save"]])

(defn init-page-state "Initialize the state of the Add/Edit page."
  []
  (atom (assoc (if (session/get :add)
                 (session/get :add)
                 (let [parent-id (get-active)
                       q (:query (url (-> js/window .-location .-href)))]
                   (merge {:add? true :bookmark/parent-id parent-id :orig-parent-id parent-id}
                          (when q {:add? true :query? true :bookmark/title (get q "title")
                                   :bookmark/url (get q "url")}))))
               :rating 0 :rating-clicked true)))

(defn add-page "Render the Add/Edit page."
  []
  [:div.col-sm-12
   [header/header]
   (let [doc (init-page-state)]
     [editor doc [bind-fields (form-template doc) doc]])])
