(ns bookmarx.env
  (:require [config.core :refer [env]]))

(defmacro cljs-env [kw]
  (env kw))