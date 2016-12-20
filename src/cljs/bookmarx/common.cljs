(ns bookmarx.common
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<!]]
            [cljs.reader :refer [read-string]]
            [cljs-http.client :as http]
            [reagent.cookies :as cookies]
            [reagent.session :as session])
  (:require-macros
    [cljs.core.async.macros :refer [go]]))

(def env (read-string js/env))

(defn set-bookmarks! "Set session bookmarks from a list of bookmarks."
  [bookmarks]
  (let [bookmark-map (into {} (map #(vector (:bookmark/id %) %) bookmarks))]
    (reset! session/state (merge bookmark-map @session/state))))

(defn load-bookmarks "Load bookmarks from the server and add them to session."
  ([] (load-bookmarks 0))
  ([revision]
   (go (let [url (str (:host-url env) (:prefix env) "/api/bookmarks"
                      (when (> revision 0) (str "/since/" revision)))
             response (<! (http/get url {:query-params {:csrf-token true} :with-credentials? false}))]
         (session/put! :csrf-token (get-in response [:headers "csrf-token"]))
         (set-bookmarks! (get-in response [:body :bookmarks]))
         (session/put! :revision (get-in response [:body :revision]))))))

(defn get-active "Get the active folder from the session or else a cookie."
  []
  (if (session/get :active)
    (session/get :active)
    (cookies/get "active" 1)))

(defn set-active! "Set the folder that is active in the session and as a cookie."
  [active]
  (session/put! :active (js/parseInt active))
  (cookies/set! "active" active {:path (:prefix env)}))

(defn sort-folder-children "Sort the children of a folder by a sort function."
  [folder sort-fn]
  (let [[l f] (map vec ((juxt filter remove) :bookmark/url (:bookmark/children folder)))]
    (update-in folder [:bookmark/children]
               #(into [] (concat (sort-by sort-fn f) (sort-by sort-fn l))))))

(defn parse-date
  "Parse a date string into a Date.
  Format: \"Apr 30 2005 17:19:43 GMT-0500 (CDT))\""
  [datetime]
  (let [date-parts (str/split (str datetime) #" ")
        time-parts (str/split (nth date-parts 4) #":")
        month (some #(when (= (nth date-parts 1) (val %)) (key %))
                    {1 "Jan" 2 "Feb" 3 "Mar" 4 "Apr" 5 "May" 6 "Jun" 7 "Jul" 8 "Aug"
                     9 "Sep" 10 "Oct" 11 "Nov" 12 "Dec"})]
    (js/Date. (js/parseInt (nth date-parts 3)) month (js/parseInt (nth date-parts 2))
              (js/parseInt (nth time-parts 0)) (js/parseInt (nth time-parts 1))
              (js/parseInt(nth time-parts 2)))))
