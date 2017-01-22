(ns bookmarx.prod
  (:require [bookmarx.core :as core]))

;; Ignore println statements in prod.
(set! *print-fn* (fn [& _]))

(core/init!)
