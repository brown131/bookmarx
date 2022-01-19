(require '[figwheel-sidecar.repl-api])

(use 'figwheel-sidecar.repl-api)

(start-figwheel!
  {:figwheel-options {:server-port 9500}
   :build-ids ["app"]
   :all-builds [{:id "app"
                 :source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
                 :compiler
                 {:main "bookmarx.dev"
                  :asset-path "/js/out"
                  :output-to "target/cljsbuild/public/js/app.js"
                  :output-dir "target/cljsbuild/public/js/out"
                  :source-map true
                  :optimizations :none
                  :pretty-print true}}]})

(cljs-repl)

