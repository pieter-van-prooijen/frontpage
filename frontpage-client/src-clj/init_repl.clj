(ns init-repl
  (:require [cljs.repl.browser]
            [frontpage-client.nashorn-repl]
            [cemerick.piggieback]
            [weasel.repl.websocket]))

;; From the cljsbuild output-dir setting
(comment (cemerick.piggieback/cljs-repl :repl-env (frontpage-client.nashorn-repl/repl-env :debug true) :output-dir "resources/public/compiled" :output-to "frontpage_client.js"))

(comment (cemerick.piggieback/cljs-repl
          :repl-env (cljs.repl.browser/repl-env :port 9000)))
(cemerick.piggieback/cljs-repl
 :repl-env (weasel.repl.websocket/repl-env :ip "0.0.0.0" :port 9000))

