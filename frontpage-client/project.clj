;;
;; Use "lein servlet run" to start the webserver and proxy on http://localhost:3000.
;;
;; Use cider-jack-in and (require 'init-repl) to start the browser repl (in this working directory)
;;
;; Use (ns test :require [...]) to pull in namespaces.


(defproject frontpage-client "0.1.0-SNAPSHOT"
  :description "Browse / Edit Solr documents in React/Om"
  :url "https://github.com/pieter-van-prooijen/frontpage"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2311"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [om "0.6.5"]
                 [secretary "1.1.0"]
                 [datascript "0.1.6"]
                 [com.cemerick/piggieback "0.1.3"]
                 [figwheel "0.1.4-SNAPSHOT"]]

  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-servlet "0.4.0"]
            [com.cemerick/clojurescript.test "0.3.1"]
            [lein-figwheel "0.1.4-SNAPSHOT"]]

  :jvm-opts ["-Xmx512M"]

  :source-paths ["src"]

  :cljsbuild { 
    :builds [{:id "dev"
              :source-paths ["src" "test"]
              :compiler {
                :output-to "frontpage_client.js"
                :output-dir "resources/public"
                :optimizations :none
                :source-map true}}]}

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  ;; Built-in jetty 9 webserver for the index.html and the reverse proxy for Solr.
  ;; (to circumvent the cross-domain XHR request restrictions when posting documents to the Solr server)
  :servlet {:deps [[lein-servlet/adapter-jetty9 "0.4.0"]
                   [org.eclipse.jetty/jetty-proxy "9.2.1.v20140609"]]
            :webapps {"/" {:servlets {"/*" org.eclipse.jetty.servlet.DefaultServlet} 
                           :public ""}
                      "/solr" {:servlets {"/*" [org.eclipse.jetty.proxy.ProxyServlet$Transparent
                                                {:proxyTo "http://localhost:8983/solr"}]}
                               :public ""}}}

  :figwheel {
   :http-server-root "public" });; this will be in resources/)
