;;
;; Use "lein servlet run" to start the webserver and proxy on http://localhost:3000.
;;
;; Use cider-jack-in and (require 'init-repl) to start the browser repl (in this working directory)
;;
;; Use (ns test :require [...]) to pull in namespaces.


(defproject frontpage-client "0.1.0-SNAPSHOT"
  :description "Browse / Edit Solr documents in React/Om"
  :url "https://github.com/pieter-van-prooijen/frontpage"

  :dependencies [[org.clojure/clojure "1.7.0"]

                 ;; Clojure deps
                 [http-kit "2.1.19"]
                 [ring "1.4.0"]

                 ;; Clojurescript deps
                 [org.clojure/clojurescript "1.7.145"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.omcljs/om "0.9.0"]
                 [sablono "0.3.6"]
                 [secretary "1.2.3"]
                 [datascript "0.13.2"]
                 [jayq "2.5.4"]
                 [com.domkm/silk "0.0.4" :exclusions [org.clojure/clojure]]

                 ;; Java deps
                 [javax.websocket/javax.websocket-api "1.1"]
                 [org.glassfish.tyrus.bundles/tyrus-standalone-client-jdk "1.10"]]

  :plugins [[lein-cljsbuild "1.1.0"]
            [lein-servlet "0.4.0"]
            [com.cemerick/clojurescript.test "0.3.1"]
            [lein-figwheel "0.4.1"]]

  ;; Invoke via "with-profile debug,default"
  :profiles {:debug {:jvm-opts ["-Xdebug" "-Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=n"]}
             :dev {:dependencies [[weasel "0.7.0"]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [figwheel "0.4.1"]]
                   :figwheel {:http-server-root "public"
                              ;; invoke (figwheel-sidecar.repl/cljs-repl) to piggieback nrepl onto the figwheel repl
                              ;; normal repl must be disabled for this to work (adjust README in figwheel or use nrepl startup ?)
                              :repl false
                              :nrepl-port 7888
                              :nrepl-middleware ["cider.nrepl/cider-middleware"
                                                 "refactor-nrepl.middleware/wrap-refactor"
                                                 "cemerick.piggieback/wrap-cljs-repl"]} }}
  
  :source-paths ["src" "src-clj"]

  :cljsbuild { 
              :builds [{:id "dev"
                        :source-paths ["src" "test"]
                        :figwheel {}
                        :compiler {:asset-path ""
                                   :output-to "resources/public/compiled/frontpage_client.js"
                                   :output-dir "resources/public/compiled"
                                   :optimizations :none
                                   :cache-analysis true         
                                   :pretty-print true         
                                   :source-map true}}]}

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  ;; Built-in jetty 9 webserver for the index.html and the reverse proxy for Solr.
  ;; (to circumvent the cross-domain XHR request restrictions when posting documents to the Solr server)
  :servlet {:deps [[lein-servlet/adapter-jetty9 "0.4.0"]
                   [org.eclipse.jetty/jetty-proxy "9.2.1.v20140609"]
                   [ring/ring-servlet "1.3.2"]
                   [org.lpetit.ring/ring-java-servlet "0.2.0" :exclusions [ring/ring-servlet]]]
            :webapps {"/" {:servlets {"/*" org.eclipse.jetty.servlet.DefaultServlet} 
                           :public "resources/public"}
                      "/nashorn" {:servlets {"/*" [org.lpetit.ring.servlet.RingHttpServlet
                                                   {:handler "frontpage-client.nashorn/handler"}]}
                                  :public ""}
                      "/solr" {:servlets {"/*" [org.eclipse.jetty.proxy.ProxyServlet$Transparent
                                                {:proxyTo "http://localhost:8983/solr"}]}
                               :public ""}}}

);; this will be in resources/
