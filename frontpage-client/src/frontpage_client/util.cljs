(ns frontpage-client.util
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.string]))

(defn html-dangerously [dom-fn attr html-text]
  "Show raw html-text in the specified dom function, using the supplied attribute map"
  (dom-fn (clj->js (merge attr {:dangerouslySetInnerHTML #js {:__html html-text}}))))

(defn icon [name]
  "Show the named icon from the open iconic font set."
  (html-dangerously dom/svg {:viewBox "0 0 8 8" :className "icon"}
                    (str "<use xlink:href=\"/open-iconic.svg#" name "\"" " class=\"" name "\"></use>")))

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
