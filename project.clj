(defproject bookmarx "2.3"
  :description "Bookmarx: Bookmark management application"
  :url "https://www.browncross.com/bookmarx/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/core.async "1.3.610"]
                 [reagent "1.0.0"]
                 [reagent-forms "0.5.44"]
                 [reagent-utils "0.3.3"]
                 [ring "1.9.2" :exclusions [ring/ring-codec]]
                 [ring/ring-defaults "0.3.2"]
                 [ring-cors "0.1.13"]
                 [ring-server "0.5.0"]
                 [ring-transit "0.1.6"]
                 [prone "2020-01-17"]
                 [com.fasterxml.jackson.core/jackson-core "2.12.2"]
                 [buddy "2.0.0"]
                 [fogus/ring-edn "0.3.0"]
                 [commons-codec "1.15"]
                 [com.taoensso/encore "3.18.0"]
                 [com.taoensso/carmine "3.1.0"]
                 [compojure "1.6.2"]
                 [hiccup "1.0.5"]
                 [com.cemerick/url "0.1.1"]
                 [yogthos/config "1.1.7"]
                 [com.taoensso/timbre "5.1.2"]
                 [org.clojure/clojurescript "1.10.773"]
                 [secretary "1.2.3"]
                 [venantius/accountant "0.2.5"]
                 [cljs-http "0.1.46"]
                 [clj-time "0.15.2"]
                 [figwheel-sidecar "0.5.20"]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-asset-minifier "0.4.6"]]

  :ring {:handler bookmarx.handler/app
         :uberwar-name "bookmarx.war"}

  :min-lein-version "2.5.0"

  :uberjar-name "bookmarx.jar"

  :main bookmarx.server

  :clean-targets ^{:protect false}
[:target-path
 [:cljsbuild :builds :app :compiler :output-dir]
 [:cljsbuild :builds :app :compiler :output-to]]

  :source-paths ["src/clj" "src/cljc"]
  :resource-paths ["resources" "target/cljsbuild"]

;  :minify-assets
;  {:assets
;   {"resources/public/css/site.min.css" "resources/public/css/site.css"}}

  :cljsbuild
  {:builds {:min
            {:source-paths ["src/cljs" "env/prod/cljs"]
             :compiler {:output-to "target/cljsbuild/public/js/app.js"
                        :output-dir "target/uberjar"
                        :optimizations :advanced
                        :pretty-print false}}
            :app
            {:source-paths ["src/cljs" "env/dev/cljs"]
             :compiler {:main "bookmarx.dev"
                        :asset-path "js/out"
                        :output-to "target/cljsbuild/public/js/app.js"
                        :output-dir "target/cljsbuild/public/js/out"
                        :source-map true
                        :optimizations :none
                        :pretty-print true}}}}

  :figwheel
  {:http-server-root "public"
   :server-port 3449
   :nrepl-port 7002
   :nrepl-middleware ["cemerick.piggieback/wrap-cljs-repl"]
   :css-dirs ["resources/public/css"]
   :ring-handler bookmarx.handler/app}

  :less {:source-paths ["src/less"]
         :target-path "resources/public/css"}

  :profiles {:dev {:repl-options {:init-ns bookmarx.repl}

                   :dependencies [[ring/ring-mock "0.4.0"]
                                  [ring/ring-devel "1.9.2" :exclusions [ring/ring-codec]]
                                  [figwheel-sidecar "0.5.20" :exclusions [args4j lcom.cognitect/transit-clj]]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [com.cemerick/piggieback "0.2.2"]
                                  [pjstadig/humane-test-output "0.11.0"]]

                   :source-paths ["env/dev/clj"]
                   :resource-paths ["env/dev/resources"]
                   :plugins [[lein-figwheel "0.5.17"]
                             [lein-less "1.7.5"]]

                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :env {:dev true}}

             :uberjar {:hooks [minify-assets.plugin/hooks]
                       :source-paths ["env/prod/clj"]
                       :resource-paths ["env/prod/resources"]
                       :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                       :uberjar-exclusions [#"org/bouncycastle"]
                       :env {:production true}
                       :aot :all
                       :omit-source true}})
