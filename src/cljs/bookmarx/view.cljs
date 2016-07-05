(ns bookmarx.view
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [goog.window :as gwin]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [bookmarx.home :as home]
            [bookmarx.add :as add])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defn home-page []
  [:div {:class "col-sm-12"}
   (home/header)
   (home/breadcrumbs)
   ;(str (:bookmark/_parent (session/get (session/get :active))))
   (doall (map #(home/bookmark %) (session/get-in [(session/get :active) :bookmark/_parent])))])

(defn about-page []
  [:div {:class "col-sm-12"}
   (home/header)
   [:h2 "About bookmarx"]
   [:div [:a {:href "/"} "go to the home page"]]])

(defn add-page []
  [:div {:class "col-sm-12"}
   (add/header)
   (add/add)])

(defn current-page []
  [:div [(session/get :current-page)]])
