(ns init-repl-nashorn
  (:require [frontpage-client.nashorn :as nashorn]
            [cemerick.piggieback]))

(cemerick.piggieback/cljs-repl :repl-env (nashorn/create-nashorn-env false)
                               :cache-analysis true 
                               :output-dir "resources/public/compiled"
                               :output-to "frontpage_client.js")
