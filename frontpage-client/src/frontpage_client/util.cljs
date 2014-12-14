(ns frontpage-client.util
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [<! >! take! chan]]
            [jayq.core :refer [$]]
            [clojure.string])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn html-dangerously [dom-fn attr html-text]
  "Show raw html-text in the specified dom function, using the supplied attribute map"
  (dom-fn (clj->js (merge attr {:dangerouslySetInnerHTML #js {:__html html-text}}))))

(defn icon [name]
  "Show the named icon from the open iconic font set."
  (html-dangerously dom/svg {:viewBox "0 0 8 8" :className "icon"}
                    (str "<use xlink:href=\"/open-iconic.svg#" name "\"" " class=\"" name "\"></use>")))

(defn xml-id [s]
  "Create a valid xml id by substituting all invalid characters with an underscore"
  (let [re (js/RegExp. "([^a-zA-Z0-9_])" "g")]
    (.replace s re "_"))) 

(defn input-value [owner ref]
  (.-value (om/get-node owner ref)))

(defn date-fields [date]
  [(.getFullYear date) (inc (.getMonth date)) (.getDate date)])

(defn printable-date [date]
  "Converts a javascript Date into something more readable."
  (clojure.string/join "-" (date-fields date)))

(defn collapse-same
  ([coll]
     "Answer a lazy seq with ranges of same value items in coll collapsed into one.
      (collapse-same [1 1 2 3 3 1 1]) => (1 2 3 1).
      Allows nil value items in the seq."
     (if (seq coll)
       (collapse-same coll (first coll))
       (empty coll)))
  ([coll v]
     (lazy-seq
      (if (and (seq coll) (= (first coll) v))
        (collapse-same (rest coll) v)
        (cons v (collapse-same coll))))))

;; Allow the update methods on an Atom containing a hash, to simulate the modifying the app state.
;; cursor contains the atom.
(extend-type Atom
  om/ITransact
  (-transact! [cursor korks f tag]
    (let [ks (if (sequential? korks) korks [korks])
          m @cursor]
      (swap! cursor update-in ks f))))

(defn possibly-deref [cursor]
  (if (om/rendering?) cursor @cursor))

(defn staged-async-exec [start-fn process-results-fn app stage-fn]
  "Start an async call with start-fn, processing the result received from chan into app with process-results-fn. 
   
   Allows for 'staged' modifications to the global app stage, e.g. changes which are needed for the input to start-fn but which should not be rendered before the function has completed and processed the results.
   stage-fn does these modifications into this staged app state."
  (let [chan (chan)
        staged-map (assoc (possibly-deref app) :staged true)
        staged-app (atom staged-map)] ; assumes stage-fn and start-fn always derefs this.
    (stage-fn staged-app)
    (start-fn staged-app chan)
    (go
      (let [result (<! chan)]
        (stage-fn app) ; apply the staged modification to the real application state.
        (process-results-fn result app)))))

;;
;; Wrap the specified react component ("owner") into a reveal modal with the specified id
;; Reveal elements are not managed by react, because they are manipulated by the reveal javascript which
;; Reacts doesn't like.
;; inner-owner is the the result of a om/build invocation.

(defn- render-to-element-with-id [component id]
  (let [root (. js/document (getElementById id))]
    (js/React.renderComponent component root)))

(defn reveal-modal [app owner {:keys [reveal-id inner-owner] :as opts}]
  (reify
    om/IRender
    (render [_]
      (html-dangerously dom/div {} (str "<div id='" reveal-id "' class='reveal-modal' data-reveal></div>")))
    om/IDidMount
    (did-mount [this]
      (render-to-element-with-id inner-owner reveal-id))
    om/IDidUpdate
    (did-update [this prev-props prev-state]
      (render-to-element-with-id inner-owner reveal-id))))

(defn do-reveal [id op]
  "Do some operation on the specified reveal modal identified by id. Op can be \"open\" or \"close\""
  (.foundation ($ (str "#" id)) "reveal" (name op)))

