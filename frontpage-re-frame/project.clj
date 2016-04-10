;; Start developing the project:
;;
;; - run solr in the main directory
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
;;   $ gulp sass:autowatch to automatically recompile/reload foundation

(defproject frontpage-re-frame "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [reagent "0.5.1"]
                 [re-frame "0.6.0"]
                 [secretary "1.2.3"]
                 [cljs-ajax "0.5.4"]
                 [prismatic/schema "1.0.5"]
                 [camel-snake-kebab "0.3.2"]
                 [jayq "2.5.4"]]

  :min-lein-version "2.5.3"


  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-figwheel "0.5.0-6"]
            [lein-servlet "0.4.1"]
            [lein-doo "0.1.6"]]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"
                                    "test/js"]

  
  :source-paths ["src/clj"]
  
  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.2.1"]
                                  ;;[org.clojure/tools.nrepl "0.2.12"]
                                  [figwheel-sidecar "0.5.0-6"]]
                   :source-paths ["src/cljs" "test/cljs" "dev"] 
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}

  :figwheel {:css-dirs ["resources/public/css"]}
  
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/cljs" "test/cljs"]
                        :figwheel {:on-jsload "frontpage-re-frame.core/mount-root"}
                        :compiler {:main frontpage-re-frame.core
                                   :output-to "resources/public/js/compiled/app.js"
                                   :output-dir "resources/public/js/compiled/out"
                                   :asset-path "js/compiled/out"
                                   :optimizations :none
                                   :source-map-timestamp true}}
                       
                       {:id "test"
                        :source-paths ["src/cljs" "test/cljs"]
                        :compiler {:output-to "resources/public/js/compiled/test.js"
                                   :main frontpage-re-frame.runner
                                   :optimizations :none}}

                       {:id "min"
                        :source-paths ["src/cljs"]
                        :compiler {:main frontpage-re-frame.core
                                   :output-to "resources/public/js/compiled/app.js"
                                   :optimizations :advanced
                                   :closure-defines {goog.DEBUG false}
                                   :pretty-print false}}]}
  

  ;; Built-in jetty 9 webserver for the index.html and the reverse proxy for Solr.
  ;; (to circumvent the cross-domain XHR request restrictions when posting documents to the Solr server)
  :servlet {:deps [[lein-servlet/adapter-jetty9 "0.4.1"]
                   [org.eclipse.jetty/jetty-proxy "9.2.6.v20141205"]]
            :webapps {"/" {:servlets {"/*" org.eclipse.jetty.servlet.DefaultServlet} 
                           :public "resources/public"}
                      "/solr" {:servlets {"/*" [org.eclipse.jetty.proxy.ProxyServlet$Transparent
                                                {:proxyTo "http://localhost:8983/solr"}]}
                               :public ""}}})