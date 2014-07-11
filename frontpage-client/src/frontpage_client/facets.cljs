(ns frontpage-client.facets
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

;; Facet list component
;; Terms: 
;; "facet" denotes a type of attribute of a document, like author or category
;; "facet value" is a particular instance of that facet, like "John Smith"

(defn toggle-facet-value [values value]
  "Toggle the value in the set of selected values"
  (om/transact! values
                (fn [values]
                  (if (get values value)
                    (disj values value)
                    (conj values value)))))


;; flat list of facet-value, count pairs, plus a set of the selected values
(defn facet-value-list [facet-value-counts selected-values on-select]
  (apply dom/ul #js {:className "side-nav"}
         (for [[facet-value count] (partition 2 facet-value-counts)]
           (dom/li (when (get selected-values facet-value) ; selected-values might be nil
                     #js {:className "active"})
                   (dom/a #js {:onClick (fn [_] 
                                          (toggle-facet-value selected-values facet-value)
                                          (on-select))}
                          (dom/span nil facet-value)
                          (dom/span nil " ")
                          (dom/span nil (str "(" count ")")))))))

;; app is a map with (at least) two keys:
;; :facet-fields, a list of facet, [facet-value-count] pairs
;; :selected-facet-values, a map of facet, #{selected-facet-value}
;; The properties can contain an on-select handler which is called when a facet value is (de-)selected.
;; (invoked with app, owner)
(defn facets-list [app owner {:keys [on-select-fn]}]
  (reify
    om/IRender
    (render [this]
      (apply dom/ul #js {:className "side-nav"}
             (for [[facet facet-value-counts] (:facet-fields app)]
               (dom/li nil
                       (dom/h4 nil (name facet))
                       (facet-value-list facet-value-counts
                                         ;; Establish a default cursor for the selected values of facet.
                                         (or (get-in app [:selected-facet-values facet])
                                             (om/update! app [:selected-facet-values facet] #{})
                                             (get-in app [:selected-facet-values facet]))
                                         (partial on-select-fn app owner))))))))
