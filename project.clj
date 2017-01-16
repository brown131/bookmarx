(defproject bookmarx "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.385" :exclusions [org.clojure/tools.reader]]
                 [reagent "0.6.0-rc"]
                 [reagent-forms "0.5.24"]
                 [reagent-utils "0.1.9"]
                 [ring "1.5.0"]
                 [ring/ring-defaults "0.2.1"]
                 [ring-cors "0.1.7"]
                 [ring-server "0.4.0"]
                 [ring-transit "0.1.6"]
                 [prone "1.1.1"]
                 [com.fasterxml.jackson.core/jackson-core "2.8.6"]
                 [buddy "1.2.0"]
                 [fogus/ring-edn "0.3.0"]
                 [commons-codec "1.10"]
                 [com.taoensso/carmine "2.15.0"]
                 [compojure "1.5.1"]
                 [hiccup "1.0.5"]
                 [com.cemerick/url "0.1.1"]
                 [yogthos/config "0.8"]
                 [com.taoensso/timbre "4.7.0"]
                 [org.clojure/clojurescript "1.9.92" :scope "provided"]
                 [secretary "1.2.3"]
                 [venantius/accountant "0.1.7" :exclusions [org.clojure/tools.reader]]
                 [cljs-http "0.1.40"]
                 [figwheel-sidecar "0.5.0"]]

  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-asset-minifier "0.2.7" :exclusions [org.clojure/clojure]]]

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

  :minify-assets
  {:assets
   {"resources/public/css/site.min.css" "resources/public/css/site.css"}}

  :cljsbuild
  {:builds {:min
            {:source-paths ["src/cljs" "src/cljc" "env/prod/cljs"]
             :compiler {:output-to "target/cljsbuild/public/js/app.js"
                        :output-dir "target/uberjar"
                        :optimizations :advanced
                        :pretty-print false}}
            :app
            {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
             :compiler {:main "bookmarx.dev"
                        :asset-path "js/out"
                        :output-to "target/cljsbuild/public/js/app.js"
                        :output-dir "target/cljsbuild/public/js/out"
                        :source-map true
                        :optimizations :none
                        :pretty-print true}}
            :devcards
            {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
             :figwheel {:devcards true}
             :compiler {:main "bookmarx.cards"
                        :asset-path "js/devcards_out"
                        :output-to "target/cljsbuild/public/js/app_devcards.js"
                        :output-dir "target/cljsbuild/public/js/devcards_out"
                        :source-map-timestamp true
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

                   :dependencies [[ring/ring-mock "0.3.0"]
                                  [ring/ring-devel "1.5.0"]
                                  [figwheel-sidecar "0.5.8"]
                                  [org.clojure/tools.nrepl "0.2.12"]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [devcards "0.2.1-7" :exclusions [org.clojure/tools.reader]]
                                  [pjstadig/humane-test-output "0.8.0"]]

                   :source-paths ["env/dev/clj"]
                   :resource-paths ["env/dev/resources"]
                   :plugins [[lein-figwheel "0.5.8"]
                             [lein-less "1.7.5"]]

                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :env {:dev true}}

             :uberjar {:hooks [minify-assets.plugin/hooks]
                       :source-paths ["env/prod/clj"]
                       :resource-paths ["env/prod/resources"]
                       :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                       :env {:production true}
                       :aot :all
                       :omit-source true}})
