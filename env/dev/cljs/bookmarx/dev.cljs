(ns ^:figwheel-no-load bookmarx.dev
  (:require [bookmarx.core :as core]
            [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

(figwheel/watch-and-reload
  :websocket-url "ws://localhost:9500/figwheel-ws"
  :jsload-callback core/mount-root)

(core/init!)
