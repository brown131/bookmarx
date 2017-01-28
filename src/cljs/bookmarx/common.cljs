(ns bookmarx.common
  (:require [clojure.string :as str]
            [reagent.cookies :as cookies]
            [reagent.session :as session]
            [cemerick.url :refer [url url-decode]]
            [cljs-http.client :as http]
            [cljs.reader :refer [read-string]])
  (:require-macros
    [cljs.core.async.macros :refer [go]]))

(defn get-cookie
  "Get an EDN cookie, first looking in the session. If not found it wll return the default."
  [key & default]
  (if-let [val (session/get key)] val
    (if-let [val (cookies/get (subs (str key) 1))]
      val
      default)))

(defn set-cookie! "Set a cookie as an EDN value, also placing it in the session."
  [key val & expire-secs]
  (let [opts {:path (get-cookie :prefix)}]
    (cookies/set! (subs (str key) 1) val (if expire-secs (assoc opts :max-age expire-secs)
                                                         opts))
    (session/put! key val)))

(defn path "Create a url with the path from the environment."
  [& args]
  (str/join (cons (get-cookie :prefix) args)))

(defn server-path "Create a url to the service with the path from the environment."
  [& args]
  (let [{:keys [:protocol :host :port :path]} (url (-> js/window .-location .-href))]
    (str/join (concat [protocol "://" host (when port (str ":" port)) (when-not (= path "/"))] args))))

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
