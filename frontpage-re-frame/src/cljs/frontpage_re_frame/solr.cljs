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

(def facet-definitions
  [{:field :author
    :title "Authors"
    :level 0}
   {:field :categories
    :title "Categories"
    :multi-valued true
    :level 0}
   {:field :created-on-year
    :title "Years"
    :level 0
    :pivot {:field :created-on-month
            :title "Months"
            :level 1
            :pivot {:field :created-on-day
                    :level 2
                    :title "Days"}}}])

(def facet-document-fields (map :field facet-definitions))

(defn pivots [])

(def SolrFacetPair
  [(s/one s/Str "facet") (s/one s/Int 1)])

;; A facet could have zero results.
(def SolrFacetResult
  [SolrFacetPair])

(def SolrFacetFields
  "a schema for a range of facets"
  {(s/pred (apply hash-set facet-document-fields)) SolrFacetResult})

(def SolrFacetPivot
  {:field s/Str
   :value (s/cond-pre s/Str s/Int)
   :count s/Int
   (s/optional-key :pivot) [(s/recursive  #'SolrFacetPivot)]})

(def SolrFacetPivots
  {s/Keyword [SolrFacetPivot]})

(defn create-field-param [fields]
  (string/join " " (map csk/->snake_case_string fields)))

(defn facet-name [facet-definition]
  (csk/->snake_case_string (:field facet-definition)))

;; Create a list of facet.pivot arguments for all facet definitions
;; Note that even a facet without children is represented as a pivot
(defn create-pivots
  ([facet-definitions selected-fields]
   (create-pivots facet-definitions selected-fields true))
  ([facet-definitions selected-fields top-level?]
   (for [facet-definition facet-definitions]
     (if (and (get selected-fields (:field facet-definition)) (:pivot facet-definition))
       ;; selected, add any child pivot fields
       (string/join "," [(facet-name facet-definition)
                         (first (create-pivots [(:pivot facet-definition)] selected-fields false))])
       ;; not selected or single level facet, repeated because solr wants two fields in the facet.pivot parameter.
       (string/join "," (repeat (if top-level? 2 1) (facet-name facet-definition)))))))

;; Extract the first part of the comma separated pivot facet and use that for the keys
;; pivot keys should be strings, not keywords.
(defn convert-pivots [facet-pivots]
  (into {}
        (for [[s v] facet-pivots]
          (if-let [matches (re-find #"^([^,]+)," s)]
            [(csk/->kebab-case-keyword (second matches)) v]
            [(keyword s) v]))))


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
                :facet.pivot (create-pivots facet-definitions fields)
                :facet.pivot.mincount 1}]
    (assoc params :fq
           (->> fields
                (map (fn [[field values]]
                       (map (fn [value]
                              (str (csk/->snake_case_string field) ":\"" value "\""))
                            values)))
                (apply concat)))))
