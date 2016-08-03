(ns bookmarx.add
  (:require [reagent.core :refer [atom]]
            [reagent.session :as session]
            [reagent-forms.core :refer [bind-fields init-field value-of]]
            [accountant.core :as accountant]
            [taoensso.timbre :as log]
            [cemerick.url :refer [url]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [bookmarx.env :refer [env get-active sort-folder-children]]
            [bookmarx.header :as header])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

(defn -clean-doc "Remove temporary fields from the page document."
  [doc]
  (dissoc @doc :bookmark/_parent :orig-parent :folder? :add? :delete? :query? :rating :rating-clicked))

(defn add-bookmark "Add a new bookmark."
  [doc]
  ;; Update the state in the remote repository.
  (go (let [body (:body (<! (http/post (str (:host-url env) (:prefix env) "/api/bookmarks")
                                       {:edn-params (-clean-doc doc)
                                        :with-credentials? false
                                        :headers {"x-csrf-token" (session/get :csrf-token)}})))
            parent-id (get-in @doc [:bookmark/parent :db/id])
            parent (session/get parent-id)]
          ;; Set the new ids.
          (swap! doc assoc @doc :db/id (:db/id body) :bookmark/id (:bookmark/id body))

          ;; Add the new folder to the session.
          (when (:folder? @doc) (session/put! (:db/id @doc) (-clean-doc doc)))
                  
          ;; Add the bookmark to the parent's children.
          (session/put! parent-id (sort-folder-children 
                                   (update-in parent [:bookmark/_parent] 
                                              #(conj % (-clean-doc doc))) :bookmark/name)))))

(defn upsert-bookmark "Upsert a bookmark."
  [doc]
  (let [parent-id (get-in @doc [:bookmark/parent :db/id])
        parent (session/get parent-id)
        orig-parent-id (get-in @doc [:orig-parent :db/id])
        orig-parent (session/get orig-parent-id)
        children (:bookmark/_parent parent)]
    ;; Replace the children of the folder with the children from the session folder.
    (when (:folder? @doc)
      (session/put! (:db/id @doc)
                    (assoc (-clean-doc doc) :bookmark/_parent
                           (:bookmark/_parent (session/get (:db/id @doc))))))

    ;; Update the parent's children with the updated child.
    (session/put! parent-id (sort-folder-children 
                             (update-in parent [:bookmark/_parent] 
                                        #(conj % (-clean-doc doc))) :bookmark/name))

    ;; Removed the bookmark from the folder that it was moved out of.
    (log/debugf "orig parent %s" (remove (fn [b] (= (:db/id b) (:db/id @doc))) 
                                         (:bookmark/_parent orig-parent)))
    (when (and orig-parent-id (not= parent-id orig-parent-id))
      (session/put! orig-parent-id 
                    (update-in orig-parent [:bookmark/_parent]
                               #(remove (fn [b] (= (:db/id b) (:db/id @doc))) 
                                        (:bookmark/_parent orig-parent))))))

  ;; Update the state in the remote repository.
  (go (<! (http/put (str (:host-url env) (:prefix env) "/api/bookmarks/" (:bookmark/id @doc))
                    {:edn-params (-clean-doc doc)
                     :with-credentials? false
                     :headers {"x-csrf-token" (session/get :csrf-token)}}))))
  
(defn delete-bookmark "Delete the bookmark on the backend service."
  [doc]
  (log/debugf "delete")

  ;; Remove the bookmark from the session.
  (when (:folder? @doc) (session/remove! (:db/id @doc)))
                  
  ;; Remove the bookmark from the parent's children.
  (let [parent-id (get-active)
        parent (session/get parent-id)
        children (:bookmark/_parent parent)]
    (session/put! parent-id
                  (update-in parent [:bookmark/_parent]
                             #(remove (fn [b] (= (:db/id b) (:db/id @doc))) children))))

  (go (<! (http/delete (str (:host-url env) (:prefix env) "/api/bookmarks/" (:bookmark/id @doc))
                       {:edn-params (-clean-doc doc)
                        :with-credentials? false
                        :headers {"x-csrf-token" (session/get :csrf-token)}}))))
      
(defn save-bookmark "Save a bookmark."
  [doc]
  (log/debugf "save %s" @doc)
  (cond (:add? @doc) (add-bookmark doc)
        (:delete? @doc) (delete-bookmark doc)
        :else (upsert-bookmark doc))
  
  ; Return to the home page.
  (accountant/navigate! (str (:prefix env) "/")))

(defn rating-star "Renders a bookmark rating star."
  [index doc]
  [:span {:class (if (<= index (if (get-in @doc [:rating-clicked])
                                 (get-in @doc [:bookmark/rating])
                                 (get-in @doc [:rating])))
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
                               (swap! doc update-in [:orig-parent] (fn [] (:bookmark/parent @doc))))
                             (if (session/get :add)
                               (session/update-in! [:add :orig-parent] (fn [] (:bookmark/parent @doc)))
                               (session/put! [:add] @doc))
                             (accountant/navigate! (str (:prefix env) "/select")))
                :href (str (:prefix env) "/select")}
   (:bookmark/name (session/get (if (:db/id (:bookmark/parent @doc))
                                  (:db/id (:bookmark/parent @doc))
                                  (get-active))))])

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
   [row "Name" [:input.form-control {:field :text :id :bookmark/name}]]
   [:div {:field :container :visible? #(not (:folder? %))}
    [row "URL" [:input.form-control {:field :text :id :bookmark/url}]]
    [row "Rating" [rating-stars doc]]]
   [row "Parent Folder" [folder-selector doc]]
   [row "Icon" [:button.btn.btn-default
                ;{:on-click #(reagent-modals/modal! [:div "some message to the user!"])} 
                "Select"]]
   [:div {:field :container :visible? #(not (:add? %))}
    [row "Delete?" [:input.form-control {:field :checkbox :id :delete?}]]]])

(defn editor [doc & body]
  [:div body
   [:button.btn.btn-default {:on-click #(save-bookmark doc)} "Save"]])

(defn init-page-state "Initialize the state of the Add/Edit page."
  []
  (atom (assoc (if (session/get :add)
                 (session/get :add)
                 (let [parent {:db/id (if (get-active) (get-active) (session/get :root))}
                       q (:query (url (-> js/window .-location .-href)))]
                   (merge {:add? true :bookmark/parent parent :orig-parent parent}
                          (when q {:add? true :query? true :bookmark/name (get q "name")
                                   :bookmark/url (get q "url")}))))
               :rating 0 :rating-clicked true)))

(defn add-page "Render the Add/Edit page."
  []
  [:div {:class "col-sm-12"}
   [header/header]
   (let [doc (init-page-state)]
     [editor doc [bind-fields (form-template doc) doc]])])
