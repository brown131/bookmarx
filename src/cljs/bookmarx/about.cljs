(ns bookmarx.about
  (:require [bookmarx.common :refer [path]]
            [bookmarx.header :as header]))

(defn about-page "Render the About page."
  []
  [:div.col-sm-12
   [header/header]
   [:h2 "About Bookmarx 2.3"]
   [:div "A personal bookmark tracker written in written in " [:a {:href "https//clojure.org"} "Clojure"] " and "
    [:a {:href "https://github.com/clojure/clojurescript"} "ClojureScript"] ". It is essentially a rewrite, sans gold-plating, of the excellent " [:a {:href "http://bookmark4u.sourceforge.net"} "Bookmark4U"]
    " which I used for many years, and whose interface and source code have unfortunately become pretty dated. Kudos to the authors of this application."]
   [:p]
    [:div "On the front-end it is using " [:a {:href "https://reagent-project.github.io/"} "Reagent"] 
    ", which is a ClojureScript wrapper for the " 
    [:a {:href "https://facebook.github.io/react/"} "React"] " user interface framework. The user interface style is the ubiquitous " [:a {:href "http://getbootstrap.com"} "Bootstrap"] 
    " framework. On the back-end it is using " [:a {:href "https://redis.io"} "Redis"] " for persistence."]
   [:p]
   [:div "This is written for my own edification and provided as-as and is not supported."]
   [:p]
   [:div "The icons in this application are from " [:a {:href "http://glyphicons.com"} "Gylphicons"] " which are part of the Bootstrap framework."]
   [:p]
   [:div [:a {:href (path "/")} "go to the home page"]]])

