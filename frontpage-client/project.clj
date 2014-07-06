;;
;; Use (require 'init-repl) to start the browser repl (in this working directory)
;; Connect to http://localhost:9000/index.html to execute commands in the browser.
;; Use (ns test :require [...]) to pull in namespaces.

(defproject frontpage-client "0.1.0-SNAPSHOT"
  :description "Browse / Edit Solr documents in React/Om"
  :url "http://example.com/FIXME"

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2234"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [om "0.6.4"]
                 [secretary "1.1.0"]
                 [datascript "0.1.4"]
                 [com.cemerick/piggieback "0.1.3"]]

  :plugins [[lein-cljsbuild "1.0.3"]
            [cider/cider-nrepl "0.6.0"]
            [com.cemerick/clojurescript.test "0.3.1"]]

  :source-paths ["src"]

  :cljsbuild { 
    :builds [{:id "frontpage-client"
              :source-paths ["src" "test"]
              :compiler {
                :output-to "frontpage_client.js"
                :output-dir "out"
                :optimizations :none
                :source-map true}}]}

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
)
