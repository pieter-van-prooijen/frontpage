(ns frontpage-client.document
  (:require [clojure.string]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontpage-client.facets :as facets]
            [frontpage-client.solr :as solr]
            [cljs.core.async :refer [<! >! chan put!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Document component for frontpage, allows in-place editing.

(enable-console-print!)

(defn metadata [doc owner]
  (reify
    om/IRender
    (render [_]
      (let [author (:author doc)
            created-on (js/Date. (:created_on doc))]
        (apply dom/div #js {:className "metadata"}
               (dom/a #js {:onClick (fn [_]
                                      (facets/select-facet owner (name :created_on) created-on))}
                      (frontpage-client.util/printable-date created-on))
               (dom/a #js {:onClick (fn [_]
                                      (facets/select-facet owner (name :author) author))} author)
               (butlast
                (interleave       ; not interpose, can't reuse components.
                 (for [category (:categories doc)]
                   (dom/a #js {:className "category"
                               :onClick (fn [_]
                                          (facets/select-facet owner (name :categories) category))}
                          category))
                 (repeatedly #(dom/span nil " - ")))))))))

(defn show-doc [doc owner {:keys [toggle-editing-fn]}]
  (reify
    om/IRender
    (render [_]
      (if doc
        (dom/div #js {:className "row"}
                 (dom/div #js {:className "large-12 columns"}
                          (dom/h2 nil (:title doc))
                          (frontpage-client.util/html-dangerously dom/div nil (:body doc))
                          (om/build metadata doc)
                          (dom/div #js {:className "row"}
                                   (dom/div #js {:className "large-12 columns"}
                                            (dom/a #js {:className "radius button inline left"
                                                        :onClick toggle-editing-fn} "Edit")))))
        (dom/div nil "No current document")))))

(defn handle-change [e owner key]
  "Set the local state of owner (under key) with the value contained in the e event."
  (let [new-value (.. e -target -value)]
    (om/set-state! owner key new-value)))

(defn commit [doc owner]
  "Adjust the doc cursor with changes from the owner component and answer the modified document"
  (let [title (om/get-state owner :title)
        body (om/get-state owner :body)
        c (chan)
        modified (-> @doc
            (assoc :title title)
            (assoc :body body))]
    (solr/put-doc modified c)
    (go 
     (let [result (<! c)]
       (print result)))
    modified))
 
(defn edit-doc [doc owner {:keys [toggle-editing-fn doc-changed-fn]}]
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
                                  (dom/textarea #js {:rows "6" :name "body" :value body
                                                     :onChange (fn [e] (handle-change e owner :body))})))
                (dom/ul #js {:className "button-group radius"}
                        (dom/li nil
                          (dom/a #js {:className "button strong"
                                      :onClick (fn [e]
                                                 (let [modified (commit doc owner)]
                                                   (doc-changed-fn modified)
                                                   (toggle-editing-fn e)))} 
                                 (dom/strong nil "Commit")))
                        (dom/li nil
                         (dom/a #js {:className "button" :onClick toggle-editing-fn} "cancel")))))))


(defn current-doc [doc owner {:keys [doc-changed-fn]}]
  "Highlight the current document"
  (reify
    om/IInitState
    (init-state [_]
      {:editing false})
    om/IRenderState 
    (render-state [_ {:keys [editing]}]
      ;; Use owner (the react component) and not "this" (reified protocol instance) with om/*-state functions.
      (let [opts {:opts {:toggle-editing-fn (fn [_] 
                                              (om/update-state! owner :editing (fn [old] (not old))))
                         :doc-changed-fn doc-changed-fn}}]
        (dom/div #js {:className "current-doc"}
                 (if editing
                   (om/build edit-doc doc opts)
                   (om/build show-doc doc opts)))))))

