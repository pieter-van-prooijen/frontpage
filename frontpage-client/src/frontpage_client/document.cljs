(ns frontpage-client.document
  (:require [clojure.string]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontpage-client.facets :as facets]
            [frontpage-client.util :as util]
            [frontpage-client.solr :as solr]
            [cljs.core.async :refer [<! >! chan put!]]
            [goog.net.HttpStatus :as httpStatus])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Document component, allows in-place editing.

(enable-console-print!)
 
(defn metadata [doc owner]
  (om/component
   (let [author (:author doc)
         created-on (js/Date. (:created_on doc))] ; solr answers an iso 8601 date.
     (apply dom/div #js {:className "metadata"}
            (dom/a #js {:onClick (fn [_]
                                   (facets/select-facet owner (name :created_on) created-on))}
                   (frontpage-client.util/printable-date created-on))
            (dom/a #js {:onClick (fn [_]
                                   (facets/select-facet owner (name :author) author))} author)
            (butlast
             (interleave      ; not interpose, can't reuse components.
              (for [category (:categories doc)]
                (dom/a #js {:className "category"
                            :onClick (fn [_]
                                       (facets/select-facet owner (name :categories) category))}
                       category))
              (repeatedly #(dom/span nil " - "))))))))

(defn show-doc [doc owner]
  (om/component
   (if doc
     (dom/div #js {:className "row"}
              (dom/div #js {:className "large-12 columns"}
                       (dom/h2 nil (:title doc))
                       (frontpage-client.util/html-dangerously dom/div nil (:body doc))
                       (om/build metadata doc)
                       (dom/div #js {:className "row"}
                                (dom/div #js {:className "large-12 columns"}
                                         (dom/a #js {:className "radius button inline left"
                                                     :onClick #(util/do-reveal (util/xml-id (:id @doc)) "open") }
                                                "Edit")))))
     (dom/div nil "No current document"))))

(defn handle-change [e owner key]
  "Set the local state of owner (under key) with the value contained in the e event."
  (let [new-value (.. e -target -value)]
    (om/set-state! owner key new-value)))

(defn commit [doc owner]
  "Adjust the doc cursor with changes from the owner component and answer the modified document"
  (let [title (om/get-state owner :title)
        body (om/get-state owner :body)
        c (chan)
        created-on (js/Date. (:created_on @doc))
        modified (-> @doc
            (assoc :title title)
            (assoc :body body)
            (assoc :created_on_year (.getFullYear created-on)) ; re-create non-stored fields
            (assoc :created_on_month (.getMonth created-on))
            (assoc :created_on_day (.getDate created-on)))]
    (solr/put-doc modified c)
    (go 
     (let [result (<! c)]
       (when-not (httpStatus/isSuccess result)
         (js/alert "Error saving document"))))
    modified))
 
(defn edit-doc [doc owner {:keys [doc-changed-fn]}]
  (reify
    om/IInitState
    (init-state [_]
      (select-keys doc [:title :body]))
    om/IRenderState
    (render-state [_ {:keys [title body]}]
      (dom/form #js {}
                (dom/div #js {:className "row"}
                         (dom/div #js {:className "large-12 columns"}
                                  (dom/label #js {:className "" :htmlFor "title"} "Title:")
                                  (dom/input #js {:name "title" :value title
                                                  :onChange (fn [e] (handle-change e owner :title))})))
                (dom/div #js {:className "row"}
                         (dom/div #js {:className "large-12 columns"}
                                  (dom/label #js {:className "" :htmlFor "body"} "Body:")
                                  (dom/textarea #js {:rows "10" :name "body"
                                                     :onChange (fn [e] (handle-change e owner :body))} body)))
                (dom/div #js {:className "row"}
                         (dom/div #js {:className "large-12 columns"}
                                  (let [id (util/xml-id (:id doc))]
                                    (dom/ul #js {:className "button-group radius"}
                                            (dom/li nil
                                                    (dom/a #js {:className "button strong"
                                                                :onClick (fn [e]
                                                                           (let [modified (commit doc owner)]
                                                                             (doc-changed-fn modified)
                                                                             (util/do-reveal id "close")))} 
                                                           (dom/strong nil "Commit")))
                                            (dom/li nil
                                                    (dom/a #js {:className "button"
                                                                :onClick #(util/do-reveal id "close")} "cancel"))))))))))


(defn current-doc [doc owner {:keys [doc-changed-fn]}]
  "Highlight / Edit the current document"
  (reify
    om/IInitState
    (init-state [_]
      {:editing false})
    om/IRenderState 
    (render-state [_ {:keys [editing]}]
      (let [reveal-id (util/xml-id (:id doc))
            opts {:opts {:reveal-id reveal-id
                         :doc-changed-fn doc-changed-fn}}
            edit-doc-owner (om/build edit-doc doc opts)]
        (dom/div #js {:className "current-doc"}
                 (om/build util/reveal-modal doc {:opts {:reveal-id reveal-id :inner-owner edit-doc-owner}})
                 (om/build show-doc doc opts))))))

