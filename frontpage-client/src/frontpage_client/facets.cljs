(ns frontpage-client.facets
  (:require [frontpage-client.solr :as solr]
            [frontpage-client.util]
            [frontpage-client.search]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [<! >! take! chan sub unsub]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

;; Facet handlers, functions and components

;; Terms: 
;; "facet" denotes a type of attribute of a document, like "author"
;; "facet value" is a particular instance of that facet, like "John Smith"
;;
;; Facet events arrive on the "facet-select" topic of the global publication in the shared state.
;; Facet names and values go through a translation layer using the i18n javascript library.


(defn- child-facet? [facet-key]
  "Answer if facet-name is a child facet"
  (some (partial = facet-key) (vals solr/facet-field-parent-child)))

(defn- clear-facet-values [app facet-key]
  "Unselect all values of the facet keyed on facet-key, including its child facets"
  (when-let [facet (get-in @app [:facets facet-key])]
    (om/update! app [:facets facet-key :selected-values] #{})
    (recur app (:child-key facet))))

(defn- toggle-facet-value [app facet-name value]
  (let [facet-key (keyword facet-name)
        ks [:facets facet-key :selected-values]
        selected-values (get-in @app ks #{})]
    (if (selected-values value)
      (let [facet (get-in @app [:facets facet-key])]
        (om/update! app ks (disj selected-values value))
        (clear-facet-values app (:child-key facet))) ; clear children
      (om/update! app ks (conj selected-values value)))))

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

(defn- subscribe-to-facet-select [app owner]
  "Retrieve the facet-select channel and handle the incoming requests in the form [<facet-name> <value>]."
  (let [c (chan)]
    (om/set-state! owner :facet-select-chan c)
    (sub (om/get-shared owner :publication) :facet-select c)
    (go-loop [{:keys [facet-name value]} (<! c)]
      (om/update! app :page 0)

      ;; Search uses staged invocation, don't render the selected facets until the search result is in.
      (frontpage-client.search/search app (fn [app] (select-facet-from-chan app facet-name value))) 
      (recur (<! c)))))

(defn unsubscribe-to-facet-select [owner]
  (unsub (om/get-shared owner :publication) :facet-select (om/get-state owner :facet-select-chan)))

(defn select-facet [owner facet-name value]
  "Handler for other components to select a facet with the specified value."
  (let [c (om/get-shared owner :publication-chan)]
    (go (>! c {:topic :facet-select :facet-name facet-name :value value}))))

(defmulti facet-value-label (fn [_ value gap] gap))

(defmethod facet-value-label :default [facet-name value gap]
  (.t js/i18n (str "facet-value." facet-name "." value) #js {:defaultValue value}))

(defmethod facet-value-label "+1YEAR" [_ value gap]
   (.getFullYear (js/Date. value)))

(defn sort-facet-counts? [facet-name]
  "Answer if the facet values of facet-name should be sorted numerically on name, not on the count"
  (#{:created_on_year :created_on_month :created_on_day} (keyword facet-name)))

(defn pair-and-sort-facet-counts [facet-name counts]
  (let [paired (partition 2 counts)]
    (if (sort-facet-counts? facet-name)
      (sort-by (comp js/parseInt first) paired)
      paired)))

(defn partition-in-pages [counts page-size]
   (partition page-size page-size () counts))

;; Answer a component showing an arrow icon and a custom click handler.

(defn page-facet [facet owner {:keys [up on-click-fn]}]
  (om/component
   (let [button-class "facet-page button tiny radius icon "]
     (if up
       (dom/a #js {:className (str button-class) :onClick on-click-fn}  
              (frontpage-client.util/icon "arrow-thick-top"))
       (dom/a #js {:className (str button-class) :onClick on-click-fn}
              (frontpage-client.util/icon "arrow-thick-bottom"))))))

(declare facet-list)

;; Recursive list of facet values and counts and any sub facets.
(defn facet-value-list [facets owner {:keys [facet-key page page-size]}]
  (om/component
   (let [facet (facet-key facets)
         facet-name (:name facet)
         paired (pair-and-sort-facet-counts facet-name (:counts facet))
         counts (nth (partition-in-pages paired page-size) page ())]
     (apply dom/ul #js {:className "side-nav"}
            (for [[facet-value count] counts]
              (let [selected (contains? (:selected-values facet) facet-value)]
                (dom/li (when selected #js {:className "active"})
                        (dom/a #js {:onClick (fn [_] (select-facet owner facet-name facet-value))}
                               (dom/span nil (facet-value-label facet-name facet-value (:gap facet)))
                               (dom/span nil " ")
                               (dom/span nil (str "(" count ")")))
                        (when-let [child-key (and selected (:child-key facet))]
                          (om/build facet-list facets {:opts {:facet-key child-key :page-size page-size}})))))))))

(defn facet-list [facets owner {:keys [facet-key page-size]}]
  (reify
        om/IInitState
        (init-state [_]
          {:page 0})
        om/IWillUpdate
        (will-update [_ next-facets next-state]
          (let [facet (facet-key next-facets)
                page (:page next-state)
                top-of-page-idx (* page page-size)
                last-idx (dec (count (:counts facet)))]
            ;; Page must stay in range when a facet is selected.
            (om/set-state! owner :page (if (< last-idx top-of-page-idx) 0 page))))
        om/IRenderState
        (render-state [_ {:keys [page]}]
          (let [facet (facet-key facets)
                facet-name (:name facet)
                facet-title (.t js/i18n (str "facet-name." facet-name))
                change-page-fn (fn [f]
                                 (fn [_]
                                   (let [new-page (f page)]
                                     (om/set-state! owner :page new-page)
                                     ;; FIXME: no other way to force an update on the inner value list?
                                     (om/update! facets [facet-key :page] new-page))))
                nof-pages (js/Math.ceil (/ (count (:counts facet)) (* page-size 2)))] ; counts are in pairs
            (dom/li nil
                    (if (child-facet? facet-key)
                      (dom/h5 nil facet-title)
                      (dom/h4 nil facet-title))
                    (when (> page 0)
                      (om/build page-facet facet {:opts {:up true
                                                         :on-click-fn (change-page-fn dec)}}))
                    (om/build facet-value-list facets
                                     {:opts {:facet-key facet-key :page page :page-size page-size}})
                    (when (< page (dec nof-pages))
                      (om/build page-facet facet {:opts {:up false
                                                         :on-click-fn (change-page-fn inc)}})))))))

(defn facets-list [app owner]
  "Render all facets contained in the facet map of facet-key => facet-def which are not child facets"
  (reify
    om/IWillMount
    (will-mount [this]
      (.init js/i18n)
      (subscribe-to-facet-select app owner))
    om/IWillUnmount
    (will-unmount [this]
      (unsubscribe-to-facet-select owner))
    om/IRender
    (render [this]
      (let [facets (:facets app)]
        (apply dom/ul #js {:className "side-nav"}
               (for [[facet-key _] facets]
                 (when-not (child-facet? facet-key)
                   (om/build facet-list facets {:opts {:facet-key facet-key :page-size 5}}))))))))

