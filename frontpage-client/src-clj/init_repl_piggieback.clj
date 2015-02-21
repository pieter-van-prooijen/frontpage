(ns init-repl-piggieback
  (:require [frontpage-client.nashorn-repl]
            [cemerick.piggieback]))

;; From the cljsbuild output-dir setting
(cemerick.piggieback/cljs-repl :repl-env (frontpage-client.nashorn-repl/repl-env)
                               :output-dir "resources/public/compiled")
