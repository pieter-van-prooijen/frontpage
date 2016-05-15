(ns piggieback-browser
  (:require 
            [weasel.repl]))

;; Can't proxy the repl, because the server part sets its url in the response
(comment (repl/connect "http://localhost:9000/repl"))
(weasel.repl/connect "ws://localhost:9000")

