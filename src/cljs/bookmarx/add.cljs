(ns bookmarx.add
  (:require [reagent.core :refer [atom]]
            [reagent.session :as session]
            [reagent-forms.core :refer [bind-fields init-field value-of]]
            [accountant.core :as accountant]
            [taoensso.timbre :as log]
            [cemerick.url :refer [url]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [bookmarx.env :refer [env get-active]]
            [bookmarx.header :as header])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

(defn row
  [label input]
  [:div.row
   [:div.col-md-2 ^{:key label} [:label label]]
   [:div.col-md-5 ^{:key input} input]])

(def form-template
  [:div
   [:div {:field :container :visible? #(and (:add? %) (not (:query? %)))}
    [row "Folder?" [:input.form-control {:field :checkbox :id :folder?}]]]
   [row "Name" [:input.form-control {:field :text :id :bookmark/name}]]
   [:div {:field :container :visible? #(not (:folder? %))}
    [row "URL" [:input.form-control {:field :text :id :bookmark/url}]]
    [row "Rating" [:input.form-control {:field :text :id :bookmark/rating}]]]
   [:div {:field :container :visible? #(not (:add? %))}
    [row "Delete?" [:input.form-control {:field :checkbox :id :delete?}]]]
   ])

(defn add-bookmark "Add a new bookmark."
  [doc]
  ;; Add the parent to the bookmark.
  (swap! doc #(assoc @doc :bookmark/parent {:db/id (session/get :active)}))

  ;; Update the state in the remote repository.
  (go (let [bookmark (dissoc @doc :bookmark/_parent :folder? :add? :query?)
            body (:body (<! (http/post (str (:host-url env) (:prefix env) "/api/bookmarks")
                                       {:edn-params bookmark
                                        :with-credentials? false
                                        :headers {"x-csrf-token" (session/get :csrf-token)}})))]
        (let [parent-id (get-active)
              parent (session/get parent-id)]
          (log/debugf "body %s" body)
            
          ;; Set the new ids and remove the add flags.
          (swap! doc #(-> @doc
                          (assoc :db/id (:db/id body)
                                 :bookmark/id (:bookmark/id body))
                          (dissoc :add? :query?))

          ;; Add the new folder to the session.
          (when (:folder? @doc) (session/put! (:db/id @doc) @doc))
                  
          ;; Add the bookmark to the parent's children.
          (session/put! parent-id (update-in parent [:bookmark/_parent] #(conj % @doc))))))))

(defn upsert-bookmark "Upsert a bookmark."
  [doc]
  (let [parent-id (get-in @doc [:bookmark/parent :db/id])
        parent (session/get parent-id)
        children (:bookmark/_parent parent)]
    ;; Replace the children of the folder with the children from the session folder.
    (when (:folder? @doc)
      (session/put! (:db/id @doc)
                    (assoc @doc :bookmark/_parent
                           (:bookmark/_parent (session/get (:db/id @doc))))))
    ;; Update the parent's children with the updated child.
    (session/put! parent-id
                  (update-in parent [:bookmark/_parent]
                             #(map (fn [b] (if (= (:db/id b) (:db/id @doc)) @doc b))
                                   children))))

  ;; Update the state in the remote repository.
  (go (let [bookmark (dissoc @doc :bookmark/_parent :folder?)]
        (<! (http/put (str (:host-url env) (:prefix env) "/api/bookmarks/" (:bookmark/id @doc))
                      {:edn-params bookmark
                       :with-credentials? false
                       :headers {"x-csrf-token" (session/get :csrf-token)}})))))
  
(defn delete-bookmark "Delete the bookmark on the backend service."
  [doc]
  (log/debugf "delete")

  ;; TODO: Remove the bookmark from the session.

  (go (let [bookmark (dissoc @doc :bookmark/_parent :folder? :add? :delete?)]
        (<! (http/delete (str (:host-url env) (:prefix env) "/api/bookmarks/" (:bookmark/id @doc))
                         {:edn-params bookmark
                          :with-credentials? false
                          :headers {"x-csrf-token" (session/get :csrf-token)}})))))
      
(defn save-bookmark "Save a bookmark."
  [doc]
  (log/debugf "save %s" @doc)
  (cond (:add? @doc) (add-bookmark doc)
        (:delete? @doc) (delete-bookmark doc)
        :else (upsert-bookmark doc))
  
  ; Return to the home page.
  (accountant/navigate! (str (:prefix env) "/")))

(defn editor [doc & body]
  [:div body
   [:button.btn.btn-default {:on-click #(save-bookmark doc)} "Save"]])

(defn add-page "Render the Add/Edit page."
  []
  [:div {:class "col-sm-12"}
   [header/header]
   (let [doc (if (session/get :add) 
                 (atom (session/get :add))
                 (let [q (:query (url (-> js/window .-location .-href)))]
                   (if q (atom {:add? true :query? true :bookmark/name (get q "name") 
                                :bookmark/url (get q "url")})
                         (atom {:add? true}))))]
     [editor doc [bind-fields form-template doc]])])
