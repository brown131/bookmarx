(defproject bookmarx "2.3"
  :description "Bookmarx: Bookmark management application"
  :url "https://www.browncross.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.3.443" :exclusions [org.clojure/tools.reader]]
                 [reagent "0.7.0"]
                 [reagent-forms "0.5.31"]
                 [reagent-utils "0.2.1"]
                 [ring "1.6.2"]
                 [ring/ring-defaults "0.3.1"]
                 [ring-cors "0.1.11"]
                 [ring-server "0.5.0"]
                 [ring-transit "0.1.6"]
                 [prone "1.1.4"]
                 [com.fasterxml.jackson.core/jackson-core "2.9.1"]
                 [buddy "2.0.0"]
                 [fogus/ring-edn "0.3.0"]
                 [commons-codec "1.10"]
                 [com.taoensso/encore "2.92.0"]                 
                 [com.taoensso/carmine "2.16.0"]
                 [compojure "1.6.0"]
                 [hiccup "1.0.5"]
                 [com.cemerick/url "0.1.1"]
                 [yogthos/config "0.9"]
                 [com.taoensso/timbre "4.10.0"]
                 [org.clojure/clojurescript "1.9.908" :scope "provided"]
                 [secretary "1.2.3"]
                 [venantius/accountant "0.2.0" :exclusions [org.clojure/tools.reader]]
                 [cljs-http "0.1.43"]
                 [clj-time "0.14.0"]
                 [figwheel-sidecar "0.5.13"]]

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

                   :dependencies [[ring/ring-mock "0.3.1"]
                                  [ring/ring-devel "1.6.2"]
                                  [figwheel-sidecar "0.5.13"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [com.cemerick/piggieback "0.2.2"]
                                  [pjstadig/humane-test-output "0.8.3"]]

                   :source-paths ["env/dev/clj"]
                   :resource-paths ["env/dev/resources"]
                   :plugins [[lein-figwheel "0.5.9"]
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
