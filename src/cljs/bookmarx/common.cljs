(ns bookmarx.common
  (:require [clojure.string :as str]
            [reagent.core :refer [atom]]
            [reagent.cookies :as cookies]
            [reagent.session :as session]
            [cemerick.url :refer [url url-decode]]
            [cljs.reader :refer [read-string]])
  (:require-macros
    [bookmarx.env :refer [cljs-env]]
    [cljs.core.async.macros :refer [go]]))

(defonce settings (atom :show-title false :show-url false :show-created false :show-last-visited false
                        :show-visits false :show-rating false :show-new false :show-visited false
                        :sort-on :title))

(defonce ticks-in-hour (* 1000 60 60))

(declare set-cookie!)

(defn get-cookie
  "Get an EDN cookie, first looking in the session. If not found it wll return the default."
  [kw & default]
  (if-let [val (session/get kw)] val
    (if-let [cookie (cookies/get (name kw))]
      (let [val (read-string (str/replace (url-decode cookie) #"\+" " "))]
        (session/put! kw val)
        val)
      (when-not (empty? default) (set-cookie! kw (first default))))))

(defn set-cookie! "Set a cookie as an EDN value, also placing it in the session."
  [kw val & expire-secs]
  (let [opts {:path (cljs-env :prefix)}
        opts (if (empty? expire-secs) opts (assoc opts :max-age (first expire-secs)))]
    (cookies/set! (name kw) val opts)
    (session/put! kw val)))

(defn path "Create a url with the path from the environment."
  [& args]
  (str/join (cons (cljs-env :prefix) args)))

(defn server-path "Create a url to the service with the path from the environment."
  [& args]
  (let [{:keys [:protocol :host :port]} (url (-> js/window .-location .-href))]
    (str protocol "://" host (if (> port 0) (str ":" port) "")
         (str/join (cons (cljs-env :prefix) args)))))

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

(defn get-route "Gets the route to a menu in a tree by id."
  ([id] (get-route id [id]))
  ([id route]
   (if-let [parent (:bookmark/parent-id (session/get id))]
     (recur parent (cons parent route))
     route)))
