(ns frontpage-re-frame.solr
  "Solr document and query definitions"
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

(def SolrFacetPair
  [(s/one s/Str "facet") (s/one s/Int 1)])

;; A facet could have zero results.
(def SolrFacetResult
  [SolrFacetPair])

(def SolrFacetFields
  "a schema for a range of facets"
  {:author SolrFacetResult})

(defn transform-doc [doc]
  (-> doc
      (assoc :created-on (js/Date. (:created-on doc)))
      (assoc :categories (set (:categories doc)))))

(defn extract-docs [response]
  (let [docs (get-in response [:response :docs])
        highlights (get-in response [:highlighting])]
    (->> docs
         (map transform-doc)
         (map (fn [doc]
                ;; make sure the id of the document matches the highlighting map keys
                (if-let [highlight (first (:text (get highlights (csk/->kebab-case-keyword (:id doc)))))]
                  (assoc doc :highlight highlight)
                  doc))))))

(def result-fields [:id :title :author :categories :created-on])
(def document-fields [:id :title :body :author :categories :created-on])
(def facet-document-fields [:author :categories :created-on-year])

(defn create-field-param [fields]
  (string/join " " (map csk/->snake_case_string fields)))

(defn search-params [{:keys [text page page-size fields]}]
  (let [params {:q text
                :wt "json"
                :start (* page page-size)
                :rows page-size
                :hl true
                :hl.fl "text"
                :hl.fragsize 300
                :fl (create-field-param result-fields)
                :facet true
                :facet.field "author" ;; FIXME, cljs-ajax translates vector args into f[0], f[1] etc.
                :facet.mincount 1}]
    (if fields
      (let [[field value] (first fields)]  ;; no multiple facet fields for now
        (assoc params :fq (str (csk/->snake_case_string field) ":\"" value "\"")))
      params)))
