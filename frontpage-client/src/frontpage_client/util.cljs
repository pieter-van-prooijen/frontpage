(ns frontpage-client.util
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(defn html-dangerously [dom-fn attr html-text]
  "Show raw html-text in the specified dom function, using the supplied attribute map"
  (dom-fn (clj->js (merge attr {:dangerouslySetInnerHTML #js {:__html html-text}}))))

(defn input-value [owner ref]
  (.-value (om/get-node owner ref)))

(defn collapse-same
  ([coll]
     "Answer a lazy seq with ranges of same value items in coll collapsed into one.
      (collapse-same [1 1 2 3 3 1 1]) => (1 2 3 1)"
     (collapse-same coll (first coll)))
  ([coll v]
     (if (seq coll)
       (lazy-seq
        (if (= (first coll) v)
          (collapse-same (rest coll) v)
          (cons v (collapse-same coll))))
       (if (nil? v)
         (empty coll)
         (conj (empty coll) v)))))
