(ns bookmarx.common
  (:require [clojure.string :as str]
            [cljs.reader :refer [read-string]]
            [reagent.cookies :as cookies]
            [reagent.session :as session]))

(def env (read-string js/env))

(defn set-active "Set the folder that is active in the session and as a cookie."
  [active]
  (cookies/set! "active" active {:path (:prefix env)})
  (session/put! :active (js/parseInt active)))

(defn get-active "Get the active folder from the cookie or else the session."
  []
  (if (session/get :active)
    (session/get :active)
    (cookies/get "active" (session/get :root))))

(defn sort-folder-children "Sort the children of a folder by a sort function."
  [folder sort-fn]
  (let [[l f] (map vec ((juxt filter remove) :bookmark/url (:bookmark/_parent folder)))]
    (update-in folder [:bookmark/_parent]
               #(into [] (concat (sort-by sort-fn f) (sort-by sort-fn l))))))

(defn parse-datomic-date
  "Parse a Datomic date string into a Date.
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
