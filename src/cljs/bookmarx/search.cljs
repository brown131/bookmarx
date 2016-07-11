(ns bookmarx.search)

(defn header "Render the header for the page."[]
  [:span
   [:nav {:class "header-nav"}
    [:div {:class "container-fluid"}
     [:span {:class "header-navbar"} "Bookmarx"]
     [:span {:class "header-navbar header-star"}]]]])

(defn search "Search for bookmarks."
  []
  )
