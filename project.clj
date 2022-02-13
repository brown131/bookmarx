(defproject bookmarx "2.3"
  :description "Bookmarx: Bookmark management application"
  :url "https://www.browncross.com/bookmarx/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/core.async "1.5.648"]
                 [org.clojure/clojurescript "1.11.4"]
                 [reagent "1.1.0"]
                 [reagent-forms "0.5.44"]
                 [reagent-utils "0.3.4"]
                 [cljsjs/react "17.0.2-0"]
                 [cljsjs/react-dom "17.0.2-0"]
                 [yogthos/config "1.1.9"]
                 [ring "1.9.5" :exclusions [ring/ring-codec]]
                 [ring/ring-defaults "0.3.3"]
                 [ring-cors "0.1.13"]
                 [ring-server "0.5.0"]
                 [ring-middleware-format "0.7.4"]
                 [prone "2021-04-23"]
                 [com.fasterxml.jackson.core/jackson-core "2.13.1"]
                 [buddy "2.0.0"]
                 [fogus/ring-edn "0.3.0"]
                 [commons-codec "1.15"]
                 [com.taoensso/encore "3.20.0"]
                 [com.taoensso/carmine "3.1.0"]
                 [com.taoensso/timbre "5.1.2"]
                 [compojure "1.6.2"]
                 [hiccup "1.0.5"]
                 [com.cemerick/url "0.1.1"]
                 [secretary "1.2.3"]
                 [venantius/accountant "0.2.5"]
                 [cljs-http "0.1.46"]
                 [clj-time "0.15.2"]]

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
                        :pretty-print true}}}}

  :less {:source-paths ["src/less"]
         :target-path "resources/public/css"}

  :profiles {:dev {:repl-options {:init-ns bookmarx.repl}

                   :dependencies [[ring/ring-mock "0.4.0"]
                                  [ring/ring-devel "1.9.5" :exclusions [ring/ring-codec]]
                                  [thheller/shadow-cljs "2.17.2"]
                                  [org.clojure/tools.nrepl "0.2.13"]]

                   :jvm-opts ["-Dconfig=env/dev/resources/config.edn"]
                   :source-paths ["env/dev/clj"]
                   :resource-paths ["env/dev/resources"]
                   :plugins [[lein-less "1.7.5"]]

                   :env {:dev true}}

             :uberjar {:hooks [leiningen.cljsbuild minify-assets.plugin/hooks]
                       :jvm-opts ["-Dconfig=env/prod/resources/config.edn"]
                       :source-paths ["env/prod/clj"]
                       :resource-paths ["env/prod/resources"]
                       :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                       :env {:production true}
                       :aot :all
                       :omit-source true}})
