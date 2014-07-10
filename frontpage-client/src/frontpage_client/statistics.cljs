(ns frontpage-client.statistics
  (:refer-clojure :exclude [count list])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [datascript :as d]))

;;
;; Maintain and display document statistics about the frontpage app.


;; prevent cursor-ification
(extend-type d/DB
  om/IToCursor
  (-to-cursor
    ([this _] this)
    ([this _ _] this)))

(defn create-conn []
  (let [schema {:categories {:db/cardinality :db.cardinality/many }}]
    (d/create-conn schema)))

(defn add [conn doc mode]
  "Register the document as being shown in a listing."
  (d/transact! conn [ {:db/id -1
                       :mode mode
                       :id (:id doc)
                       :author (:author doc)
                       :categories (:categories doc)}]))

(defn list [conn attr]
  "List all values which have the attribute."
  (d/q '[:find ?v :in $ ?attr :where [_ ?attr ?v]] @conn attr))

(defn count [conn mode]
  "Count the number of entries which have the specified mode."
  (let [result (d/q '[:find (count ?e) :in $ ?m :where [?e :mode ?m]] @conn mode)]
    (if (seq result) (first (first result)) 0)))

(defn doc-ids [docs]
  "Answer a set of all the ids in docs"
  (set (map :id docs)))
 
(defn tx-listen [conn tx-data root-cursor]
  (let [path (:path tx-data)
        new-docs (get-in tx-data [:new-state :docs])
        old-docs (get-in tx-data [:old-state :docs])
        old-doc-ids (doc-ids old-docs)
        new-doc-ids (doc-ids new-docs)]
    (when-not (= old-doc-ids new-doc-ids)
      (doseq [doc new-docs]
        (add conn doc :listed)))
    ;; TODO: use clojure.core/match ?
    (when (= (first path) :docs)
      (let [new-doc (get new-docs (second path))
            old-doc (get old-docs (second path))]
        (add conn new-doc :selected)
        (when (and old-doc (not= old-doc new-doc))
          (add conn new-doc :edited))))))
      

;; Statistics component
;; Answer facts about listed components etc.
(defn statistics [app owner]
  (reify
    om/IRender
    (render [this]
      (dom/table nil
       (dom/thead nil
        (dom/tr nil
         (dom/td nil "mode")
         (dom/td nil "count"))
        (apply dom/tbody nil
               (for [mode [:listed :selected :edited]]
                 (dom/tr nil
                         (dom/td nil (name mode))
                         (dom/td nil (count (om/get-shared owner :db) mode))))))))))





 
