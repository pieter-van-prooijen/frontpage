(ns init-repl-test
  (:require [cljs.repl]
            [frontpage-client.nashorn-repl]))

(def env (frontpage-client.nashorn-repl/repl-env :debug true))
(cljs.repl/repl env :output-dir "resources/public/compiled" )
