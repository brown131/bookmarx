(ns bookmarx.auth
  (:require [buddy.hashers :as hs]
            [taoensso.carmine :as car]))

(defn add-user! [user]
  (store/add-user! ds (update-in user [:password] #(hs/encrypt %))))
