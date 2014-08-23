(ns frontpage-client.figwheel
  (:require [figwheel.client :include-macros true]))

(enable-console-print!)

;; Figwheel automatic code reloading, start autobuild + figwheel with "lein figwheel"

(figwheel.client/watch-and-reload
  :websocket-url   "ws://localhost:3449/figwheel-ws" ; jetty webserver.
  :jsload-callback (fn [] (print "reloaded files")))

