(ns frontpage-client.util
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.string]))

(defn html-dangerously [dom-fn attr html-text]
  "Show raw html-text in the specified dom function, using the supplied attribute map"
  (dom-fn (clj->js (merge attr {:dangerouslySetInnerHTML #js {:__html html-text}}))))

(defn input-value [owner ref]
  (.-value (om/get-node owner ref)))

(defn printable-date [s]
  "Converts an iso 8601 date into something more readable."
  (let [date (js/Date.)
        _ (.setTime date (js/Date.parse s))
        year (.getFullYear date)
        month (inc (.getMonth date)) ; zero based
        day (.getDate date)]
    (clojure.string/join "-" [day month year])))

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
