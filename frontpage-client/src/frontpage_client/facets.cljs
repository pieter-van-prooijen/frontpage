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
;; :page-size (defined in opts).
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


(defn clear-facet-values [app facet-key]
  "Unselect all values of the facet keyed on facet-key, including its child facets"
  (when-let [facet (get-in @app [:facets facet-key])]
    (om/update! app [:facets facet-key :selected-values] #{})
    (recur app (:child-key facet))))

(defn toggle-facet-value [app facet-name value]
  "Toggle the value in the set of selected values of facet."
  (let [facet-key (keyword facet-name)
        keys [:facets facet-key :selected-values]
        selected-values (get-in @app keys #{})]
    (if (selected-values value)
      (let [facet (get-in @app [:facets facet-key])]
        (om/update! app keys (disj selected-values value))
        (clear-facet-values app (:child-key facet)))
      (om/update! app keys (conj selected-values value)))))

;; Set the specified facet and value, making sure the facet definition is complete.
(defn- create-and-set-facet [app facet-keyword value]
  (om/update! app [:facets facet-keyword] {:name  (name facet-keyword) :selected-values #{value}}))

;; Dispatch on the various types of selects coming through the channel
(defmulti select-facet-from-chan (fn [app name value]
                                   (condp instance? value
                                     js/Date :date
                                     :default)))

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
  "Handler for other components to select a facet with the specified value."
  (let [c (om/get-shared owner :facet-select-chan)]
    (go (>! c [facet-name value]))))

(defmulti facet-value-label (fn [_ value gap] gap))

(defmethod facet-value-label :default [facet-name value gap]
  (.t js/i18n (str "facet-value." facet-name "." value) #js {:defaultValue value}))

(defmethod facet-value-label "+1YEAR" [_ value gap]
   (.getFullYear (js/Date. value)))

(declare facet-list)


(defn sort-facet-counts? [facet-name]
  "Answer if the facet counts of facet-name should be sorted numerically, not on the count"
  (#{:created_on_year :created_on_month :created_on_day} (keyword facet-name)))

(defn pair-and-sort-facet-counts [facet-name counts]
  (let [paired (partition 2 counts)]
    (if (sort-facet-counts? facet-name)
        (sort-by (comp js/parseInt first) paired)
        paired)))

(defn partition-in-pages [counts page-size]
   (partition page-size page-size () counts))

;; Check if the page number of a facet is still valid for the current selection, answer the
;; first page a selected value is on or the current page
(defn adjust-page [selected-values sorted-counts page-size default]
  (nth 
   (remove nil?
           (for [[page page-part] 
                 (partition 2 (interleave (range) (partition-in-pages sorted-counts page-size)))
                 [value _] page-part]
             (when (get selected-values value)
               page)))
   0 default))

  ;; flat list of facet-value, count pairs, plus a set of the selected values
(defn facet-value-list [facets owner facet page-size]
  (apply dom/ul #js {:className "side-nav"}
         (let [facet-name (:name facet)
               paired (pair-and-sort-facet-counts facet-name (:counts facet))
               page (adjust-page (:selected-values facet) paired page-size (get facet :page 0))
               _ (om/update! facet :page page)
               counts (nth (partition-in-pages paired page-size) page ())] ; FIXME, gives out-of-bounds ?
           (for [[facet-value count] counts]
             (let [selected (contains? (:selected-values facet) facet-value)]
               (dom/li (when selected #js {:className "active"})
                       (dom/a #js {:onClick (fn [_] (select-facet owner facet-name facet-value))}
                              (dom/span nil (facet-value-label facet-name facet-value (:gap facet)))
                              (dom/span nil " ")
                              (dom/span nil (str "(" count ")")))
                       (when-let [child-key (and selected (:child-key facet))]
                         ;; child could not be present yet, because the search result is asynchronous.
                         ;; will be rerendered.
                         (when (child-key facets)
                           (facet-list facets owner (child-key facets))))))))))


(defn facet-list [facets owner facet]
  (let [facet-name (:name facet)
        facet-title (.t js/i18n (str "facet-name." facet-name))
        page-size 5]
    (dom/li nil
            (if (child-facet? facet-name)
              (dom/h5 nil facet-title)
              (dom/h4 nil facet-title))
            (om/build page-facet facet {:opts {:up true, :page-size page-size}})
            (facet-value-list facets owner facet page-size)
            (om/build page-facet facet {:opts {:up false, :page-size page-size}}))))


(defn facets-list [facets owner]
  "Render all facets contained in the facet map of facet-key => facet-def which are not child facets"
  (reify
    om/IWillMount
    (will-mount [this]
      (.init js/i18n))
    om/IRender
    (render [this]
      (apply dom/ul #js {:className "side-nav"}
             (for [[facet-field facet] facets]
               (when-not (child-facet? facet-field)
                 (facet-list facets owner facet)))))))

