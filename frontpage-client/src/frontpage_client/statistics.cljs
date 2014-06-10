(ns frontpage-client.statistics
  (:refer-clojure :exclude [count list])
  (:require [om.core :as om :include-macros true]
            [datascript :as d]))

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
 





 
