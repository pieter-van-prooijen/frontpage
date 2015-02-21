(ns frontpage-client.xml-http-request
  (:require [frontpage-client.util :as util]))

(util/set-print!)

;; Define a stub object which can do simple http requests to solr.
;; Uses a predefined httpSender object which implements frontpage-client.nashorn/IHTTPSender
(defprotocol IXMLHttpRequest
  (open [this method url async user password])
  (send [this ])
  (abort [this]))

(defrecord XMLHttpRequest []
  IXMLHttpRequest
  (open [this method url async user password]
    (set! (.-method this) method)
    (set! (.-url this) url)
    (set! (.-readyState this) 1)
    (set! (.-responseText this) nil)
    (set! (.-timeout this) 200))
  (send [this]
    (let [result-arr (.get-url js/httpSender (.-url this) (.-timeout this))]
      (set! (.-status this) (aget result-arr 0))
      (set! (.-responseText this) (aget result-arr 1))
      (set! (.-readyState this) 4)
      (.onreadystatechange this)))
  (abort [this]))

;; Create a function which applies  f with this as its first argument, appended with 
;; the rest of the supplied argument.
;; Bridges object and protocol methods.
(defn- apply-with-this [f]
  (fn [& args]
    (this-as this (apply f this args))))

;; Alias to the global request object and method names
(set! js/XMLHttpRequest frontpage-client.xml-http-request/XMLHttpRequest)
(set! (.. js/XMLHttpRequest -prototype -open) (apply-with-this open))
(set! (.. js/XMLHttpRequest -prototype -send) (apply-with-this send))
(set! (.. js/XMLHttpRequest -prototype -abort) (apply-with-this abort))

nil

