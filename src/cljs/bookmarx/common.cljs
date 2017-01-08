(ns bookmarx.common
  (:require [clojure.string :as str]
            [cljs.reader :refer [read-string]]
            [reagent.cookies :as cookies]
            [reagent.session :as session]))

(def env (read-string js/env))

(defn path "Create a url with the path from the environment."
  [& args]
  (apply str (cons (:prefix bookmarx.common/env) args)))

(defn server-path "Create a url to the service with the path from the environment."
  [& args]
  (str apply (concat [(:host-url bookmarx.common/env) (:prefix bookmarx.common/env)] args)))

(defn get-active "Get the active folder from the session or else a cookie."
  []
  (if (session/get :active) (session/get :active) (cookies/get "active" 1)))

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
