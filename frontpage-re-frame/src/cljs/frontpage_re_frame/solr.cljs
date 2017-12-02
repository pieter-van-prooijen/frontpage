(ns frontpage-re-frame.solr
  "Solr document and query definitions"
  (:require [frontpage-re-frame.spec-utils :as spec-utils]
            [clojure.string :as string]
            [cljs.spec.alpha :as spec]
            [camel-snake-kebab.core :as csk]))

(spec/def ::id ::spec-utils/non-blank)
(spec/def ::title ::spec-utils/non-blank)
(spec/def ::author ::spec-utils/non-blank)
(spec/def ::created-on ::spec-utils/date-time)
(spec/def ::categories (spec-utils/set-of ::spec-utils/non-blank))
(spec/def ::highlight string?) ; highlights are empty for '*' queries
(spec/def ::body ::spec-utils/non-blank)

;; single document queries don't have a highlight
;; search results don't have body.
(spec/def ::document (spec/keys :req-un [::id ::title ::author ::created-on ::categories]
                                :option-un [::highlight ::body]))

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
    :pivot [{:field :created-on-month
              :title "Months"
              :level 1
             :pivot [{:field :created-on-day
                      :title "Days"
                      :level 2}]}]}])

(def facet-fields (into #{} (comp (map :field) (remove nil?))
                        (tree-seq
                         (fn [node] (contains? node :pivot)) ; branch?
                         (fn [node] (:pivot node)) ; children
                         {:pivot facet-definitions}))) ; artificial root node, gives nil in tree-seq sequence


(defn child-fields [field facet-defs-arg]
  "Answer a list of field plus any child fields defined somewhere in facet-defs-arg"
  (loop [facet-defs facet-defs-arg]
    (let [facet-def (first facet-defs)
          [child-facet-def] (:pivot facet-def)]
      (when facet-def
        (if (= (:field facet-def) field)
          ;; match on this level, append all its pivot children
          (cons field (child-fields (:field child-facet-def) [child-facet-def]))
          ;; field doesn't match this level, try its pivot or move to the next on this level
          (if-let [children (child-fields field [child-facet-def])]
            children
            (recur (rest facet-defs))))))))

 
(spec/def ::field facet-fields)
(spec/def ::value (comp not nil?)) ; day / month / year facets give numbers.
(spec/def ::count ::spec-utils/pos-int) ; facet should not be reported if not present
(spec/def ::facet-pivot (spec/keys :req-un [::field ::value ::count] :opt-un [::pivot]))
(spec/def ::pivot (spec/coll-of ::facet-pivot :kind vector?))

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
                         (first (create-pivots (:pivot facet-definition) selected-fields false))])
       ;; not selected or single level facet, repeated because solr wants two fields in the facet.pivot parameter.
       (string/join "," (repeat (if top-level? 2 1) (facet-name facet-definition)))))))

;; Extract the first part of the comma separated pivot facet and use that for the keys
;; pivot keys should be strings, not keywords.
;; The field key should have a keyword value.
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
