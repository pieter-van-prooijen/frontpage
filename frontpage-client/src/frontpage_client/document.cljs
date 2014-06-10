(ns frontpage-client.document
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontpage-client.statistics :as statistics]
            [frontpage-client.solr :as solr]
            [cljs.core.async :refer [<! >! chan put!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Document component for frontpage, allows in-place editing.

(enable-console-print!)

(defn metadata [doc]
  (dom/div #js {:className "metadata"}
           (dom/span nil (:created_on doc))
           (dom/span nil (:author doc))
           (dom/span nil (clojure.string/join " - "(:categories doc)))))

(defn show-doc [doc owner {:keys [toggle-editing-fn]}]
  (reify
    om/IRender
    (render [this]
      (if doc
        (dom/div #js {:className "row"}
                 (dom/h2 nil (:title doc))
                 (frontpage-client.util/html-dangerously dom/div nil (:body doc))
                 (metadata doc)
                 (dom/div #js {:className "row"}
                          (dom/div #js {:className "large-12 columns"}
                                   (dom/a #js {:className "radius button inline left"
                                               :onClick toggle-editing-fn} "Edit"))))
        (dom/div nil "No current document")))))

(defn handle-change [e owner key]
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
    (init-state [this]
      (select-keys doc [:title :body]))
    om/IRenderState
    (render-state [this {:keys [title body]}]
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
                (dom/div #js {:className "row"}
                         (dom/div #js {:className "large-1 columns"}
                                  (dom/a #js {:className "radius button inline"
                                              :onClick (fn [e]
                                                         (let [modified (commit doc owner)]
                                                              (doc-changed-fn modified)
                                                              (toggle-editing-fn e)))} "Commit"))
                         (dom/div #js {:className "large-9 columns"}
                                  (dom/a #js {:onClick toggle-editing-fn} "cancel")))))))


(defn current-doc [doc owner {:keys [doc-changed-fn]}]
  "Highlight the current document"
  (reify
    om/IInitState
    (init-state [this]
      {:editing false})
    om/IRenderState 
    (render-state [this {:keys [editing]}]
      ;; Use owner (the react component) and not "this" (reified protocol instance) with om/*-state functions.
      (let [opts {:opts {:toggle-editing-fn (fn [_] 
                                              (om/update-state! owner :editing (fn [old] (not old))))
                         :doc-changed-fn doc-changed-fn}}]
        (dom/div #js {:className "current-doc"}
                 (if editing
                   (om/build edit-doc doc opts)
                   (om/build show-doc doc opts)))))))

