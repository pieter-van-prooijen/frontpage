(ns frontpage-client.nashorn-repl
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [cljs.analyzer :as ana]
            [cljs.env :as env]
            [cljs.repl :as repl]
            [cljs.compiler :as comp]
            [cljs.closure :as closure])
  (:import [javax.script ScriptEngineManager]))

;; Nashorn Clojurescript repl binding.
;;
;; ** Usage from Leiningen:
;;
;; Create a file init_repl_test.clj containing (adjust :output-dir to your cljsbuild settings):
;; (ns init-repl-test
;;   (:require [cljs.repl]
;;             [frontpage-client.nashorn-repl]))
;;
;; (def env (frontpage-client.nashorn-repl/repl-env))
;; (cljs.repl/repl env :output-dir "resources/public/compiled")
;;
;; Invoke it with:
;; lein trampoline run -m clojure.main src-clj/init_repl_test.clj
;;
;; ** Usage from nrepl / piggieback, execute the following at the nrepl prompt:
;;    (adjust :output-dir to your cljsbuild settings)

;; (ns init-repl-piggieback
;;   (:require [frontpage-client.nashorn-repl]
;;             [cemerick.piggieback]))
;;
;; (cemerick.piggieback/cljs-repl :repl-env (frontpage-client.nashorn-repl/repl-env)
;;                                :output-dir "resources/public/compiled")
;;
;;
;; Uses the Nashorn load() function to load Javascript files into the script engine.
;;
;; Nashorn's load() function docs:
;; http://docs.oracle.com/javase/8/docs/technotes/guides/scripting/nashorn/shell.html
;;
;; Some functions are borrowed from: https://github.com/bodil/cljs-nashorn and the node.js repl code

(defn create-engine []
  (if-let [engine (.getEngineByName (ScriptEngineManager.) "nashorn")]
    (let [context (.getContext engine)]
      (.setWriter context *out*)
      (.setErrorWriter context *err*)
      engine)
    (throw (IllegalArgumentException.
            "Cannot find the Nashorn script engine, use a JDK version 8 or higher."))))

(defn eval-resource 
  "Evaluate a file on the classpath in the engine."
  [engine path debug]
  (let [r (io/resource path)]
    (.eval engine (slurp r))
    (when debug (println "loaded: " path))))

(defn init-engine [engine output-dir debug]
  (eval-resource engine "goog/base.js" debug)
  (eval-resource engine "goog/deps.js" debug)
  (.eval engine (format (str "var nashorn_load = function(path) {"
                             "  var outputPath = \"%s\" + \"/\" + path;"
                             (when debug "  print(\"loading: \" + outputPath) ; ")
                             "  load(outputPath);"
                             "};")
                        output-dir))
  (.eval engine (str "goog.global.CLOSURE_IMPORT_SCRIPT = function(path) {"
                     " nashorn_load(\"goog/\" + path);"
                     " return true;"
                     "};"))
  (.eval engine "goog.global.isProvided_ = function(name) { return false; };")
  engine)

(defn load-js-file [engine file]
  (.eval engine (format "nashorn_load(\"%s\");" file)))

;; Create a minimal build of Clojurescript from the core library.
;;
;; Copied rom clj.cljs.repl.node.
(defn bootstrap-repl [engine output-dir opts]
  (env/ensure
   (let [deps-file ".nashorn_repl_deps.js"
         core (io/resource "cljs/core.cljs")
         core-js (closure/compile core
                                  (assoc opts :output-file (closure/src-file->target-file core)))
         deps (closure/add-dependencies opts core-js)]
     ;; output unoptimized code and the deps file
     ;; for all compiled namespaces
     (apply closure/output-unoptimized
            (assoc opts :output-to (.getPath (io/file output-dir deps-file)))
            deps)
     ;; load the deps file so we can goog.require cljs.core etc.
     (load-js-file engine deps-file))))

(defn load-ns [engine ns]
  (.eval engine (format "goog.require(\"%s\");" (comp/munge (first ns)))))

(defn- stacktrace  [e]
  (apply str (interpose "\n" (map #(str " " (.toString %)) (.getStackTrace e)))))

(def repl-filename "<cljs repl>")

(defrecord NashornEnv [engine debug]
  repl/IReplEnvOptions
  (-repl-options [this] {})
  repl/IJavaScriptEnv
  (-setup [this  {:keys [output-dir bootstrap output-to] :as opts}]
    (init-engine engine output-dir debug)
    (let [env (ana/empty-env)]
      (if output-to
        (load-js-file engine output-to)
        (bootstrap-repl engine output-dir opts))
      (repl/evaluate-form this env repl-filename  
                          '(do                                  
                             (.require js/goog "cljs.core")
                             (set! cljs.core/*print-fn* js/print)))))

  (-evaluate [{engine :engine :as this} filename line js]
    (when debug (println "Evaluating: " js))
    (try {:status :success
          :value (if-let [r (.eval engine js)] (.toString r) "")}
         (catch Throwable e
           {:status :exception
            :value (.toString e)
            :stacktrace (stacktrace e)})))
  (-load [{engine :engine :as this} ns url]
    (load-ns engine ns))
  (-tear-down [this]))

(defn repl-env 
  "Create a Nashorn repl-env for use with the repl/repl* method in Clojurescript and as the
   :repl-env argument to piggieback/cljs-repl. Opts has the following extra parameters:
   
   :output-dir  the directory of the compiled files, e.g. \"resources/public/my-app\" (mandatory).
   :output-to   load this file initially into Nashorn, relative to output-dir.
                Use a minimal bootstrapped cljs.core environment if not specified."
  [& {debug :debug :as opts}]

  (let [engine (create-engine)
        compiler-env (env/default-compiler-env)
        env (merge (NashornEnv. engine debug)
                   {:cljs.env/compiler compiler-env}  ; required by cider middleware ?
                   opts)]
    env))

  
