(ns bookmarx.views
  (:require [reagent.session :as session]
            [bookmarx.components :as comp]))

(enable-console-print!)

(defn home-page []
  [:div {:class "col-sm-12"}
   (comp/header)
   (comp/breadcrumbs)
   ;(str (:bookmark/_parent (session/get (session/get :active))))
   (doall (map #(comp/bookmark %) (session/get-in [(session/get :active) :bookmark/_parent])))
   [:div [:a {:href "/about"} "go to about page"]]])

(defn about-page []
  [:div {:class "col-sm-12"}
   (comp/header)
   [:h2 "About bookmarx"]
   [:div [:a {:href "/"} "go to the home page"]]])

(defn current-page []
  [:div [(session/get :current-page)]])