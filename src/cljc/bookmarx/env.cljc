(ns bookmarx.env
  #?(:clj (:require [config.core :refer [env]])))

(defmacro cljs-env [kw]
  (env kw))
