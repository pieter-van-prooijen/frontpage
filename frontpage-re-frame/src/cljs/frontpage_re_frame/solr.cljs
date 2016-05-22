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

(def facets
  [{:field :author
    :title "Authors"}
   {:field :categories
    :title "Categories"
    :multi-valued true}
   {:field :created-on-year
    :title "Years"}])

(def facet-document-fields (map :field facets))
(def SolrFacetPair
  [(s/one s/Str "facet") (s/one s/Int 1)])

;; A facet could have zero results.
(def SolrFacetResult
  [SolrFacetPair])

(def SolrFacetFields
  "a schema for a range of facets"
  {(s/pred (apply hash-set facet-document-fields)) SolrFacetResult})

(defn create-field-param [fields]
  (string/join " " (map csk/->snake_case_string fields)))

(defn set-field-parameter [fields field value multi-valued]
  "Set the field parameter in the fields map, adding / removing if the field is multi-valued.
   Returns the new value of the fields map"
  )

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
                :facet.field (map csk/->snake_case_string facet-document-fields)
                :facet.mincount 1}]
    (assoc params :fq
           (->> fields
                (map (fn [[field values]]
                       (map (fn [value]
                              (str (csk/->snake_case_string field) ":\"" value "\"")))))
                (concat)))))
