(ns frontpage-re-frame.handlers
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [re-frame.core :as re-frame]
            [frontpage-re-frame.db :as db]
            [ajax.core :as ajax]
            [schema.core :as s]
            [camel-snake-kebab.core :as csk]
            [frontpage-re-frame.solr :as solr]))

;; Make cljs-ajax use multiple query string parameter names for vector values
(def interceptors [(ajax/ProcessGet. ajax/params-to-str-alt)])

(declare to-kebab-case-keyword)

(def default-search-params  {:page 0 :page-size 10 :nof-pages 0 :text ""})
(defn initialize-db  [_ _]
   (-> db/default-db
       (assoc :search-params default-search-params)))

(re-frame/register-handler :initialize-db initialize-db)

(defn search [db _]
   (ajax/GET "http://localhost:3000/solr/frontpage/select"
        {:format :json
         :response-format :json
         :keywords? false ; handled by kebab-case-keyword
         :params (solr/search-params (:search-params db))
         :handler #(re-frame/dispatch [:search-result %])
         :error-handler #(re-frame/dispatch [:search-error %])
         :interceptors interceptors})
   db)

(re-frame/register-handler :search search)

(defn get-document [db [_ id]]
  "Retrieve a single full document from Solr."
  (ajax/GET "http://localhost:3000/solr/frontpage/select"
       {:format :json
        :response-format :json
        :keywords? false ; handled by kebab-case-keyword
        :params {:q (str "id:\"" id "\"") :wt "json" :fl (solr/create-field-param solr/document-fields)}
        :handler #(re-frame/dispatch [:get-document-result %])
        :error-handler #(re-frame/dispatch [:search-error %])
        :interceptors interceptors})
  db)

(re-frame/register-handler :get-document get-document)

(defn search-with-text [db [_ query-text]]
  "Search with a new query from the text box"
  (if-not (string/blank? query-text)
    (let [db (assoc db :search-params (merge default-search-params {:text query-text}))]
      (re-frame/dispatch [:search])
      (assoc-in db [:search-params :fields] {}))
    (assoc-in db [:search-params :text] "")))

(re-frame/register-handler :search-with-text
                           [(db/validate [:search-params] db/SearchParams)]
                           search-with-text)

(defn facet-children [field]
  "answer the children fields of field using the hierarchical facet definitions in the frontpage-re-frame.solr"
  (rest (solr/child-fields field solr/facet-definitions)))

(defn search-with-fields [db [_ field-name-values fields-only? ]]
  (re-frame/dispatch [:search])
  (as-> db current-db
      (reduce (fn [db [field-name field-value remove?]]
                (db/update-fields-parameter db field-name field-value (facet-children field-name) remove?))
              current-db field-name-values)
      (if fields-only?
        (assoc-in current-db [:search-params :text] "*:*") ; only search on fields, not in the text
        current-db)
      (assoc-in current-db [:search-params :page] 0)))

(re-frame/register-handler :search-with-fields
                           [(db/validate [:search-params] db/SearchParams)]
                           search-with-fields)

(defn search-with-page [db [_ page]]
  (let [db (assoc-in db [:search-params :page] page)]
    (re-frame/dispatch [:search])
    db))

(re-frame/register-handler :search-with-page
                           [(db/validate [:search-params] db/SearchParams)]
                           search-with-page)

(def search-result-validator
  (s/validator [(s/one (s/eq :search-items) "type")
                (s/one [solr/SolrDocument] "documents")
                (s/one s/Num "nof-documents")
                (s/one solr/SolrFacetPivots "facet-pivots")]))

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

(re-frame/register-handler :search-result
                           [(db/validate [:search-result] search-result-validator)]
                           search-result)

(defn search-error [db [_ response]]
  (let [msg (get-in response [:response :error :msg] (:status-text response))]
    (assoc db :search-result [:search-error msg])))

(re-frame/register-handler :search-error
                           [(db/validate [:search-result] [(s/one (s/eq :search-error) "type") (s/one s/Str "error-str")])]
                           search-error)

(defn get-document-result [db [_ solr-response]]
  (let [response (to-kebab-case-keyword solr-response)
        docs (solr/extract-docs response)]
    (if (= (count docs) 1)
      (assoc db :document-result (first docs))
      db)))

(re-frame/register-handler :get-document-result
                           [(db/validate [:document-result] solr/SolrDocument)]
                           get-document-result)

(re-frame/register-handler
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
(defn to-kebab-case-keyword [x]
  (letfn [(convert-kv [[k v]]
            [(mem-to-kebab-case-keyword k) v])
          (convert-map [x]
            (if (map? x) (into {} (map convert-kv x)) x))]
    (walk/postwalk convert-map x)))

(re-frame/register-handler
 :toggle-debug
 (fn [db _]
   (assoc db :debug (not (:debug db)))))
