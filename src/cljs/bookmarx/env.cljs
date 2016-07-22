(ns bookmarx.env
  (:require [cljs.reader :refer [read-string]]))

(def env (read-string js/env))
