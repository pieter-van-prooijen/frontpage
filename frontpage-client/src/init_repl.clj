(ns init-repl
  (:require [cljs.repl.browser]
            [cemerick.piggieback]))

(cemerick.piggieback/cljs-repl
  :repl-env (cljs.repl.browser/repl-env :port 9000))

