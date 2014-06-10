(ns piggieback-browser
  (:require [clojure.browser.repl :as repl]))

;; Can't proxy the repl, because the server part sets its url in the response
(repl/connect "http://localhost:9000/repl")
