(ns frontpage-client.util
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(defn html-dangerously [dom-fn attr html-text]
  "Show raw html-text in the specified dom function, using the supplied attribute map"
  (dom-fn (clj->js (merge attr {:dangerouslySetInnerHTML #js {:__html html-text}}))))

(defn input-value [owner ref]
  (.-value (om/get-node owner ref)))
