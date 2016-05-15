(ns frontpage-client.nashorn
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [cljs.analyzer :as ana]
            [cljs.repl :as repl]
            [cljs.repl.nashorn :as nashorn]
            [cljs.env :as env]
            [org.httpkit.client :as http]
            [ring.middleware.keyword-params :as ring-keyword-params]
            [ring.middleware.params :as ring-params]
            [ring.middleware.file :as ring-file]
            [ring.middleware.file-info :as ring-file-info]
            [ring.util.response :as ring-response]))


(defprotocol IHTTPSender
  "Define a simple protocol to send http requests to an url and answer the resulting response."
  (get-url [this url timeout]
    "Send the request (using the method string) to the url (also a string). Answers a tupple of status and response."))

(def http-sender 
  (reify
    IHTTPSender
    (get-url [_ url timeout]
      (let [response (http/get url {:timeout timeout})
            {:keys [status body error]} @response]
        (if error
          ;; Nashorn can marshall the vectors as javascript arrays.
          [500 error]
          [status body])))))


(def base "resources/public")
(defn make-absolute [f]
  (string/join "/" [base f]))

(def nashorn-file "<nashorn env>")

;;
;; Use the nashorn repl to get source-mapped stacktraces etc.
;;

(defn evaluate-form [env form]
  (let [ana-env (ana/empty-env)
        opts env]  ; env is merged with the opts given to nashorn/repl-env
    (repl/evaluate-form env ana-env nashorn-file form identity opts)))

(defn render [nashorn-env url]
  (evaluate-form nashorn-env `(frontpage-client.core/render-from-nashorn ~url)))

;; Put the rendered output into the index template.
(defn render-with-html [nashorn-env url]
  (let [html (slurp (make-absolute "index.html"))]
    (->> (string/split-lines html)
         (map (fn [line] (if (.contains line "NASHORN")
                           (render nashorn-env url)
                           line)))
         (string/join "\n"))))

;; Ringhandler for the Nashorn engine

;; TODO: make this an object pool ?
(def ^:dynamic *nashorn-env* (atom nil))

(defn create-nashorn-env [debug]
  (env/with-compiler-env (env/default-compiler-env)
  (let [env (nashorn/repl-env :debug debug
                              :cache-analysis true
                              :source-map true)
        _ (repl/-setup env {:source-map true
                            :output-to "frontpage_client.js"
                            :output-dir "resources/public/compiled"})]
    (.put (:engine env) "httpSender" http-sender) ; for XmlHttpRequest emulation
    ;; Load the application and stubs into the engine
    ;; FIXME: (require 'name-space) doesn't work ?
    (evaluate-form env '(js/goog.require "frontpage_client.stubs"))
    (evaluate-form env '(js/goog.require "frontpage_client.xml_http_request"))
    (evaluate-form env '(js/goog.require "frontpage_client.core"))
    env)))


(defn nashorn-handler [request]
  (when-not @*nashorn-env*
    (reset! *nashorn-env* (create-nashorn-env true)))

  ;; A "q" parameter is required.
  (if (string/blank? (:q (:params request)))
    (-> (ring-response/response "q parameter required")
        (ring-response/status 400))
    (let [url (str (:uri request) "?" (:query-string request))]
      (println "Rendering with url: " url)
      (-> (ring-response/response (render-with-html @*nashorn-env* url))
          (ring-response/content-type  "text/html")))))

(defn wrap-nocache [handler]
  (fn [request]
    (let [response (handler request)]
      (assoc-in response [:headers "cache-control"] "private, max-age=0, no-cache"))))

(def handler 
  (-> nashorn-handler
      (ring-keyword-params/wrap-keyword-params)
      (ring-params/wrap-params)
      (wrap-nocache)
      (ring-file/wrap-file base [:index-files? false])
      (ring-file-info/wrap-file-info)))

