(ns bookmarx.route
  (:require [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [bookmarx.add :as add]
            [bookmarx.home :as home]
            [bookmarx.view :as view]))

(enable-console-print!)

(secretary/defroute "/" [] (session/put! :current-page #'home/home-page))

(secretary/defroute "/about" [] (session/put! :current-page #'view/about-page))

(secretary/defroute "/add" [] (session/put! :current-page #'view/add-page))
