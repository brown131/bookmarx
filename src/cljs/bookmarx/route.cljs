(ns bookmarx.route
  (:require [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [bookmarx.about :as about]
            [bookmarx.add :as add]
            [bookmarx.home :as home]))

(secretary/defroute "/" [] (session/put! :current-page #'home/home-page))

(secretary/defroute "/about" [] (session/put! :current-page #'about/about-page))

(secretary/defroute "/add" [] (session/put! :current-page #'add/add-page))

(defn current-page "Render the current page."
  []
  [:div [(session/get :current-page)]])
