;; Start developing the project:
;;
;; - run-solr in the main directory
;; - use "lein servlet run" to run a webserver on port 3000 and open the browser onto this page.
;; - jack into cider from emacs
;;
;; - from the repl:
;;   > (start-fighweel!)
;;
;; - Open a cljs repl (app must be running in the browser):
;;   > (cljs-repl)
;;
;; In resources/public/scss:
;;   $ gulp sass:watch to automatically recompile/reload foundation
;;
;; Use "lein doo phantom test" to run the tests in PhantomJS
;; (see the :doo config below and test/cljs/frontpage-reframe/runner.cljs)
;;

(defproject frontpage-re-frame "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0-RC1"]
                 [org.clojure/clojurescript "1.9.946"]
                 [org.clojure/spec.alpha "0.1.143"]
                 [re-frame "0.10.2"]
                 [secretary "1.2.3"]
                 [cljs-ajax "0.7.3"]
                 [camel-snake-kebab "0.4.0"]]

  :min-lein-version "2.5.3"


  :plugins [[lein-cljsbuild "1.1.5"]
            [lein-figwheel "0.5.14" :exclusions [org.clojure/clojure org.clojure/tools.reader clj-time joda-time]]
            [lein-servlet "0.4.1"]
            [lein-ancient "0.6.14"] ; default version 0.6.10 doesn't work
            [lein-doo "0.1.8" :exclusions [org.clojure/tools.reader]]]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"
                                    "test/js"]

  :source-paths ["src/clj"]
  
  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.2.2"]                                  
                                  [figwheel-sidecar "0.5.14"]] ; should be the same as the main figwheel version
                   :source-paths ["src/cljs" "test/cljs" "dev"] 
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}

  :figwheel {:css-dirs ["resources/public/css"]}
  
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/cljs" "test/cljs" "dev"]
                        :figwheel {:on-jsload "frontpage-re-frame.core/mount-root"}
                        :compiler {:main frontpage-re-frame.core
                                   :output-to "resources/public/js/compiled/app.js"
                                   :output-dir "resources/public/js/compiled/out"
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
                                   :output-to "resources/public/js/compiled/app.js"
                                   :optimizations :advanced
                                   :closure-defines {goog.DEBUG false}
                                   :output-dir "resources/public/js/compiled/out-min"
                                   :pretty-print false}}]}

  :doo {:paths {:phantom "/home/pieter/projects/Nuon/my-nuon-btc/node_modules/phantomjs-prebuilt/lib/phantom/bin/phantomjs"}}

  ;; Built-in jetty 9 webserver for the index.html and the reverse proxy for Solr.
  ;; (to circumvent the cross-domain XHR request restrictions when posting documents to the Solr server)
  ;; Start with 'lein servlet run'
  :servlet {:deps [[lein-servlet/adapter-jetty9 "0.4.1" :exclusions [org.glassfish/javax.el]]
                   [org.eclipse.jetty/jetty-proxy "9.4.8.v20171121"]]
            :webapps {"/" {:servlets {"/*" org.eclipse.jetty.servlet.DefaultServlet} 
                           :public "resources/public"}
                      "/solr" {:servlets {"/*" [org.eclipse.jetty.proxy.ProxyServlet$Transparent
                                                {:proxyTo "http://localhost:8983/solr"}]}
                               :public ""}}})
