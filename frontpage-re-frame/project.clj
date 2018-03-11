;; Start developing the project:
;;
;; - run-solr in the main directory
;;
;; In this project:
;; - use "lein servlet run" to run a webserver on port 3000 and open the browser onto this page.
;; - jack into cider from emacs
;;
;; - from the repl:
;;   > (start-fighweel!)
;;
;; - Open a cljs repl (app must be running in the browser):
;;   > (cljs-repl)
;;
;; - scss is in ../bulma/frontpage.scss, compile this to the resources/public/css/frontpage-bulma.css using
;;   the run.sh script in the bulma project
;;
;; Doo test runner:
;;   (phantomjs doesn't work anymore due to missing ES6 shims ?)
;;   slimerjs doesn't work with the latest firefox releases
;;   use "lein doo chrome-headless test"  to run on chrome headless, needs karma, karma-chrome-launcher
;;    and karam-cljs-test npm modules installed somewhere.
;;
;;   (see the :doo config below and test/cljs/frontpage-reframe/runner.cljs)
;;
;; To enable re-frame-10x, use the C-h key

(defproject frontpage-re-frame "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.64"]
                 [org.clojure/spec.alpha "0.1.143"]
                 [re-frame "0.10.5" :exclusions [reagent]]

                 ;; Use react 0.16
                 [reagent "0.8.0-alpha2"
                  :exclusions [cljsjs/react cljsjs/react-dom cljsjs/react-dom-server]]
                 [cljsjs/react-dom "16.2.0-3"]
                 
                 [secretary "1.2.3"]
                 [cljs-ajax "0.7.3"]
                 [camel-snake-kebab "0.4.0"]]

  :min-lein-version "2.5.3"


  :plugins [[lein-cljsbuild "1.1.5"]
            [lein-figwheel "0.5.14" :exclusions [org.clojure/clojure org.clojure/tools.reader clj-time joda-time]]
            [lein-servlet "0.4.1"] ; locally modified copy for up-to-date jetty support
            [lein-ancient "0.6.14"] ; default version 0.6.10 doesn't work
            [lein-doo "0.1.8" :exclusions [org.clojure/tools.reader]]]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"
                                    "test/js"]

  :source-paths ["src/clj"]
  
  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.2.2"]                                  
                                  [figwheel-sidecar "0.5.15"]
                                  [day8.re-frame/re-frame-10x "0.2.0-react16"]
                                  [doo "0.1.8"]] ; should be the same as the main figwheel version
                   :source-paths ["src/cljs" "test/cljs" "dev"] 
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}

  :figwheel {:css-dirs ["resources/public/css"]}
  
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/cljs" "test/cljs" "dev"]
                        :figwheel {:on-jsload "frontpage-re-frame.core/mount-root"}
                        :compiler {:main frontpage-re-frame.core
                                   :output-to "resources/public/js/compiled/app.js"
                                   :output-dir "resources/public/js/compiled/out"
                                   :closure-defines {"re_frame.trace.trace_enabled_QMARK_" true}
                                   :preloads [day8.re-frame-10x.preload]
                                   :asset-path "js/compiled/out"
                                   :optimizations :none
                                   :source-map-timestamp false}} ; timestamped sources don't work in firefox ?
                       
                       {:id "test"
                        :source-paths ["src/cljs" "test/cljs" "dev"]
                        :compiler {:output-to "resources/public/js/compiled/test.js"
                                   :main frontpage-re-frame.runner
                                   :optimizations :none}}

                       {:id "min"
                        :source-paths ["src/cljs"]
                        :compiler {:main frontpage-re-frame.core
                                   :output-to "resources/public/js/compiled/app-min.js"
                                   :optimizations :advanced
                                   :closure-defines {goog.DEBUG false}
                                   :externs ["resources/public/vendor/foundation-6/js/vendor/jquery.ext.js"]
                                   :output-dir "resources/public/js/compiled/out-min"
                                   :pretty-print false}}]}

  :doo {:paths {:phantom "/home/pieter/packages/phantomjs-2.1.1-linux-x86_64/bin/phantomjs"
                :slimer "/home/pieter/packages/slimerjs-1.0.0-beta.1/slimerjs"
                :karma "/home/pieter/projects/Clojure/frontpage/karma/node_modules/karma/bin/karma"}}

  ;; Built-in jetty 9 webserver for the index.html and the reverse proxy for Solr.
  ;; (to circumvent the cross-domain XHR request restrictions when posting documents to the Solr server)
  ;; Start with 'lein servlet run'
  ;; Note that 4.1 is a custom version updated to the latest jetty9
  :servlet {:deps [[lein-servlet/adapter-jetty9 "0.4.1" :exclusions [org.glassfish/javax.el]]
                   [org.eclipse.jetty/jetty-proxy "9.4.8.v20171121"]]
            :webapps {"/" {:servlets {"/*" org.eclipse.jetty.servlet.DefaultServlet} 
                           :public "resources/public"}
                      "/solr" {:servlets {"/*" [org.eclipse.jetty.proxy.ProxyServlet$Transparent
                                                {:proxyTo "http://localhost:8983/solr"}]}
                               :public ""}}})
