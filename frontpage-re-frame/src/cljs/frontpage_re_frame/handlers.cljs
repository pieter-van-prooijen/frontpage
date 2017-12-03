(ns frontpage-re-frame.handlers
  (:require [frontpage-re-frame.spec-utils :as spec-utils]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [re-frame.core :as re-frame]
            [frontpage-re-frame.db :as db]
            [ajax.core :as ajax]
            [cljs.spec.alpha :as spec]
            [camel-snake-kebab.core :as csk]
            [frontpage-re-frame.solr :as solr]))

(declare to-kebab-case-keyword)

(re-frame/reg-event-db
 :initialize-db
 (fn [_ _]
   db/default-db))

;; Generic ajax request effect
(re-frame/reg-fx
 :http
 (fn [{:keys [url params success-event failure-event]}]
   (ajax/GET url {:format :json
                  :response-format :json
                  :keywords? false ; handled by kebab-case-keyword
                  :params params
                  :handler #(re-frame/dispatch [success-event %])
                  :error-handler #(re-frame/dispatch [failure-event %])
                  :vec-strategy :java})))

(re-frame/reg-event-fx
 :search
 (fn [{db :db} _]
   {:db (assoc-in db [:search-result] [:loading])
    :http {:url "http://localhost:3000/solr/frontpage/select"
           :params (solr/search-params (:search-params db))
           :success-event :search-result
           :failure-event :search-error}}))


(re-frame/reg-event-fx
 :get-document
 (fn [_ [_ id]]
   "Retrieve a single full document from Solr."
   {:http {:url "http://localhost:3000/solr/frontpage/select"
           :params {:q (str "id:\"" id "\"") :wt "json" :fl (solr/create-field-param solr/document-fields)}
           :success-event :get-document-result
           :failure-event :search-error}}))

(re-frame/reg-event-fx
 :search-with-text
 [(db/validate [:search-params] ::db/search-params)]
 (fn [{db :db} [_ query-text]]
   "Search with a new query from the text box"
   (if-not (string/blank? query-text)
     {:db (assoc db :search-params (merge db/default-search-params {:text query-text :fields {}}))
      :dispatch [:search]}
     {:db (assoc-in db [:search-params :text] "")})))

(defn facet-children [field]
  "answer the children fields of field using the hierarchical facet definitions in the frontpage-re-frame.solr"
  (rest (solr/child-fields field solr/facet-definitions)))

(defn search-with-fields [{db :db} [_ field-name-values fields-only? ]]
  {:db (as-> db current-db
         (reduce (fn [db [field-name field-value remove?]]
                   (db/update-fields-parameter db field-name field-value (facet-children field-name) remove?))
                 current-db field-name-values)
         (if fields-only?
           (assoc-in current-db [:search-params :text] "*:*") ; only search on fields, not in the text
           current-db)
         (assoc-in current-db [:search-params :page] 0))
   :dispatch [:search]})

(re-frame/reg-event-fx
 :search-with-fields
 [(db/validate [:search-params] ::db/search-params)]
 search-with-fields)

(re-frame/reg-event-fx
 :search-with-page
 [(db/validate [:search-params] ::db/search-params)]
 (fn [{db :db} [_ page]]
   {:db (assoc-in db [:search-params :page] page)
    :dispatch [:search]}))

(spec/def ::search-result (spec/cat :type #(= % :search-items)
                                    :documents (spec/coll-of ::solr/document)
                                    :nof-documents ::spec-utils/zero-or-pos-int
                                    :facet-pivots (spec/map-of solr/facet-fields (spec/coll-of ::solr/facet-pivot))))

(defn search-result [db [_ solr-response]]
  "Translate a Solr search response and store it in the db under :search-result"
  (let [response (to-kebab-case-keyword solr-response)
        docs (solr/extract-docs response)
        nof-found (get-in response [:response :num-found] 0)
        page-size (get-in db [:search-params :page-size])
        facets (get-in response [:facet-counts :facet-pivot] {})
        converted-facets (solr/convert-pivots facets)]
    (-> db
        (assoc :search-result [:search-items docs nof-found converted-facets])
        (assoc-in [:search-params :nof-pages] (js/Math.ceil (/ nof-found page-size))))))

(re-frame/reg-event-db :search-result
                       [(db/validate [:search-result] ::search-result)]
                       search-result)

(defn search-error [db [_ response]]
  (let [msg (get-in response [:response :error :msg] (:status-text response))]
    (assoc db :search-result [:search-error msg])))

(re-frame/reg-event-db :search-error
                       [(db/validate [:search-result]
                                     (spec/cat :type #(= :search-error) :error-string :spec-util/non-blank))]
                       search-error)

(defn get-document-result [db [_ solr-response]]
  (let [response (to-kebab-case-keyword solr-response)
        docs (solr/extract-docs response)]
    (if (= (count docs) 1)
      (assoc db :document-result (first docs))
      db)))

(re-frame/reg-event-db :get-document-result
                       [(db/validate [:document-result] ::solr/document)]
                       get-document-result)

(re-frame/reg-event-db
 :remove-document-result
 (fn [db _]
   (dissoc db :document-result)))

(def mem-to-kebab-case-keyword
  (memoize
   (fn [x]
     ;; Don't convert the comma separated facet pivot names
     (if (and (string? x) (not (string/index-of x ",")))
       (csk/->kebab-case-keyword x)
       x))))

;; Not that this function does not convert keywords!
;; The :field key should also have a keyword value.
(defn to-kebab-case-keyword [x]
  (letfn [(convert-kv [[k v]]
            (let [k-keyword (mem-to-kebab-case-keyword k)]
              (if (= k-keyword :field)
                [k-keyword (mem-to-kebab-case-keyword v)]
                [k-keyword v])))
          (convert-map [x]
            (if (map? x) (into {} (map convert-kv x)) x))]
    (walk/postwalk convert-map x)))

(re-frame/reg-event-db
 :toggle-debug
 (fn [db _]
   (update-in db [:debug] not)))
 
