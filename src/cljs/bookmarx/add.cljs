(ns bookmarx.add
  (:require [reagent.core :refer [atom]]
            [reagent.session :as session]
            [reagent-forms.core :refer [bind-fields init-field value-of]]
            [accountant.core :as accountant]
            [taoensso.timbre :as log]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [bookmarx.env :refer [env]]
            [bookmarx.header :as header])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defn row
  [label input]
  [:div.row
   [:div.col-md-2 ^{:key label} [:label label]]
   [:div.col-md-5 ^{:key input} input]])

(def form-template
  [:div
   [:div {:field :container :visible? #(:add? %)}
    [row "Folder?" [:input.form-control {:field :checkbox :id :folder?}]]]
   [row "Name" [:input.form-control {:field :text :id :bookmark/name}]]
   [:div {:field :container :visible? #(not (:folder? %))}
     [row "URL" [:input.form-control {:field :text :id :bookmark/url}]]
     [row "Rating" [:input.form-control {:field :text :id :bookmark/rating}]]
   ]])

(defn upsert "Upsert a bookmark by updating the appropriate state."
  [doc]
  (log/debug "upsertg")
  (if (:add? @doc)
    (let [parent-id (session/get :active)
          parent (session/get parent-id)]
      ; Add the parent.
      (swap! doc #(assoc @doc :bookmark/parent {:db/id parent-id}))
      ; Add the bookmark to the parent's children.
      (session/put! parent-id (update-in parent [:bookmark/_parent] #(conj % @doc)))
      (session/put! (:db/id @doc) @doc))
    (let [parent-id (get-in @doc [:bookmark/parent :db/id])
          parent (session/get parent-id)
          children (:bookmark/_parent parent)]
      ; Replace the children of the folder with the children from folder in the session.
      (when (:folder? @doc)
        (session/put! (:db/id @doc)
                      (assoc @doc :bookmark/_parent
                                  (:bookmark/_parent (session/get (:db/id @doc))))))
      ; Update the parent's children with the updated child.
      (session/put! parent-id
                    (update-in parent [:bookmark/_parent]
                               #(map (fn [b] (if (= (:db/id b) (:db/id @doc)) @doc b))
                                     children)))))

  ; Update the state in the remote repository.
  (log/debugf "bookmark: %s" (str @doc))
  (go (let [bookmark (dissoc @doc :bookmark/_parent :folder? :add?)
           response (<! (http/post (str (:host-url env) (:prefix env) "/api/bookmark")
                                {:edn-params bookmark
                                 :with-credentials? false
                                 :headers {"x-csrf-token" (session/get :csrf-token)}}))]
        (log/debugf "response %s" response)))

  ; Return to the home page.
  (accountant/navigate! (str (:prefix env) "/")))

(defn editor [doc & body]
  [:div body
   [:button.btn.btn-default {:on-click #(upsert doc)} "Save"]])

(defn add-page "Render the Add/Edit page."
  []
  [:div {:class "col-sm-12"}
   [header/header]
   (let [doc (if (session/get :add) (atom (session/get :add)) (atom {:add? true}))]
     [editor doc [bind-fields form-template doc]])])
