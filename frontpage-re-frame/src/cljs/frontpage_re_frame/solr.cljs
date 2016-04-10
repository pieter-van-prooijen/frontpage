(ns frontpage-re-frame.solr
  (:require [schema.core :as s]
            [clojure.string :as string]
            [camel-snake-kebab.core :as csk]))

(def SolrDocument
  "a schema for a single Solr document"
  {:id s/Str
   :title s/Str
   :author s/Str
   :created-on s/Inst
   (s/optional-key :highlight) s/Str ; single document queries don't have a highlight
   (s/optional-key :body) s/Str ; search results don't have a body
   :categories (s/pred not-empty #{s/Str})})

;; Normalize data and names at the outside of your system
(defn normalize-keys [doc]
  (->> doc
       (map (fn [[key value]]
              [(csk/->kebab-case-keyword key) value]))
       (apply concat)
       (apply hash-map)))

(defn transform-doc [doc]
  (-> doc
      (assoc :created-on (js/Date. (:created-on doc)))
      (assoc :categories (set (:categories doc)))))

(defn extract-docs [response]
  (let [docs (get-in response [:response :docs])
        highlights (get-in response [:highlighting])]
    (->> docs
         (map normalize-keys)
         (map transform-doc)
         (map (fn [doc]
                (if-let [highlight (first (:text (get highlights (keyword (:id doc)))))]
                  (assoc doc :highlight highlight)
                  doc))))))

(def result-fields [:id :title :author :categories :created-on])
(def document-fields [:id :title :body :author :categories :created-on])

(defn create-fl-param [fields]
  (string/join " " (map csk/->snake_case_string fields)))

(defn search-params [{:keys [text page page-size]}]
  {:q text
   :wt "json"
   :start (* page page-size)
   :rows page-size
   :hl true
   :hl.fl "text"
   :hl.fragsize 300
   :fl (create-fl-param result-fields)})
