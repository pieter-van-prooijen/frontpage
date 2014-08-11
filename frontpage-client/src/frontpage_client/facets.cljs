(ns frontpage-client.facets
  (:require [frontpage-client.solr :as solr]
            [frontpage-client.util]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [<! >! take! chan]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

;; Facet list component and handlers
;; Terms: 
;; "facet" denotes a type of attribute of a document, like author or category
;; "facet value" is a particular instance of that facet, like "John Smith"
;;
;; Assumes a "facet-select-chan" channel is present in the shared state.
;;

;; Answer a component which can hide or reveal more facet values, in multiples of 
;; :page-size. De opts key :up
(defn page-facet [facet owner opts]
  (om/component
   (let [page-size (:page-size opts)
         nof-pages (js/Math.ceil (/ (count (:counts facet)) (* page-size 2))) ; counts are in pairs
         page (get facet :page 0)
         button-class "facet-page button tiny radius icon "]
     (if  (:up opts)
       (dom/a #js {:className (str button-class (when (<= page 0) "hide")) :onClick
                   (fn [_] (om/update! facet :page (dec page)))}  
              (frontpage-client.util/icon "arrow-thick-top"))
       (dom/a #js {:className (str button-class (when (>= page (dec nof-pages)) "hide"))
                   :onClick (fn [_] (om/update! facet :page (inc page)))}
              (frontpage-client.util/icon "arrow-thick-bottom"))))))


(defn child-facet? [facet-name]
  "Answer if facet-name is a child facet"
  (some (partial = facet-name) (vals solr/facet-field-parent-child)))

(defn unselect-facet [facet]
  "Unselect all values from facet"
  (om/update! facet [:selected-values] #{}))

(defn toggle-facet-value [app facet-name value]
  "Toggle the value in the set of selected values of facet"
  (om/transact! app [:facets (keyword facet-name)] ; facets are keyed on keyword.
                (fn [facet]
                  (let [values (get facet :selected-values #{})]
                    (->> (if (values value)
                          (do
                            ;; Unset any selected child facets of this facet.
                            (if-let [child-name (:child-name facet)]
                              (unselect-facet (get-in @app [:facets child-name])))
                            (disj values value))
                          (conj values value))
                        (assoc facet :selected-values))))))

;; Dispatch on the various types of selects coming through the channel
(defmulti select-facet-from-chan (fn [app name value]
                                   (condp instance? value
                                     js/Date :date
                                     :default)))

;; Set the specified facet and value, making sure the facet definition is complete.
(defn- create-and-set-facet [app facet-keyword value]
  (om/update! app [:facets facet-keyword] {:name  (name facet-keyword) :selected-values #{value}}))

;; Selecting a date displays all articles published on that day.
(defmethod select-facet-from-chan :date [app facet-name date]
  (let [[year month day] (frontpage-client.util/date-fields date)]
    (create-and-set-facet app :created_on_year year)
    (create-and-set-facet app :created_on_month month)
    (create-and-set-facet app :created_on_day day)
    (om/update! app :q "*")))

(defmethod select-facet-from-chan :default [app facet-name value]
                        (toggle-facet-value app facet-name value))

(defn install-facet-select-loop [app owner]
  "Retrieve the facet-select channel and handle the incoming requests in the form [<facet-name> <value> <command>]."
  (go-loop [c (om/get-shared owner :facet-select-chan)]
           (let [[facet-name value] (<! c)]
             (select-facet-from-chan app facet-name value)
             (om/update! app :page 0)
             (frontpage-client.core/search app owner) 
             (recur c))))

(defn select-facet [owner facet-name value]
  "Handler for other components to select a facet with the specified value. command can be :clear-q, to 
   clear the current full text query."
  (let [c (om/get-shared owner :facet-select-chan)]
    (go (>! c [facet-name value]))))

(defmulti facet-value-label (fn [value gap] gap))

(defmethod facet-value-label :default [value gap]
  value)

(defmethod facet-value-label "+1YEAR" [value gap]
   (.getFullYear (js/Date. value)))

(declare facet-list)

;; flat list of facet-value, count pairs, plus a set of the selected values
(defn facet-value-list [facets owner facet page-size]
  (apply dom/ul #js {:className "side-nav"}
         (let [page (get facet :page 0)
               page-size-elements (* page-size 2)  ; facet counts are in pairs
               counts (nth (partition page-size-elements page-size-elements () (:counts facet)) page)]
           (for [[facet-value count] (partition 2 counts)]
             (let [selected (contains? (:selected-values facet) facet-value)
                   facet-name (:name facet)]
               (dom/li (when selected #js {:className "active"})
                       (dom/a #js {:onClick (fn [_] (select-facet owner facet-name facet-value))}
                              (dom/span nil (facet-value-label facet-value (:gap facet)))
                              (dom/span nil " ")
                              (dom/span nil (str "(" count ")")))
                       (when-let [child-key (and selected (:child-key facet))]
                         (facet-list facets owner (child-key facets)))))))))


(defn facet-list [facets owner facet]
  (let [facet-name (:name facet)
        page-size 5]
    (dom/li nil
            (if (child-facet? facet-name)
              (dom/h5 nil facet-name)
              (dom/h4 nil facet-name))
            (om/build page-facet facet {:opts {:up true, :page-size page-size}})
            (facet-value-list facets owner facet page-size)
            (om/build page-facet facet {:opts {:up false, :page-size page-size}}))))


(defn facets-list [facets owner]
  "Render all facets containded in the facet map of facet-key => facet-def"
  (reify
    om/IRender
    (render [this]
      (apply dom/ul #js {:className "side-nav"}
             (for [[facet-field facet] facets]
               (when-not (child-facet? facet-field)
                 (facet-list facets owner facet)))))))

