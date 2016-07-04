(require '[figwheel-sidecar.repl-api])

(use 'figwheel-sidecar.repl-api)

(start-figwheel!
  {:figwheel-options {:server-port 3449}
   :build-ids ["app" "devcards"]
   :all-builds [{:id "app"
                 :source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
                 :compiler
                 {:main "bookmarx.dev"
                  :asset-path "/js/out"
                  :output-to "target/cljsbuild/public/js/app.js"
                  :output-dir "target/cljsbuild/public/js/out"
                  :source-map true
                  :optimizations :none
                  :pretty-print  true}}
                {:id "devcards"
                 :source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
                 :figwheel {:devcards true}
                 :compiler
                 {:main "bookmarx.cards"
                  :asset-path "js/devcards_out"
                  :output-to "target/cljsbuild/public/js/app_devcards.js"
                  :output-dir "target/cljsbuild/public/js/devcards_out"
                  :source-map-timestamp true
                  :optimizations :none
                  :pretty-print true}}]})

(cljs-repl)

