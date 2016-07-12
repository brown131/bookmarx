(ns bookmarx.about)

(defn header "Render the header for the page."
  []
  [:span
   [:nav {:class "header-nav"}
    [:div {:class "container-fluid"}
     [:span {:class "header-navbar"} "Bookmarx"]
     [:span {:class "header-navbar header-star"}]]]])

(defn about-page "Render the About page."
  []
  [:div {:class "col-sm-12"}
   [header]
   [:h2 "About bookmarx"]
   [:div [:a {:href "/"} "go to the home page"]]])

