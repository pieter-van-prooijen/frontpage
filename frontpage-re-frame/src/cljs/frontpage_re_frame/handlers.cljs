(ns frontpage-re-frame.handlers
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [re-frame.core :as re-frame]
            [frontpage-re-frame.db :as db]
            [ajax.core :refer [GET]]
            [schema.core :as s]
            [camel-snake-kebab.core :as csk]
            [frontpage-re-frame.solr :as solr]))

(declare to-kebab-case-keyword)

(def default-search-params  {:page 0 :page-size 10 :nof-pages 0})
(defn initialize-db  [_ _]
   (-> db/default-db
       (assoc :search-params default-search-params)))

(re-frame/register-handler :initialize-db initialize-db)

(defn search [db _]
   (GET "http://localhost:3000/solr/frontpage/select"
        {:format :json
         :response-format :json
         :keywords? true
         :params (solr/search-params (:search-params db))
         :handler #(re-frame/dispatch [:search-result %])
         :error-handler #(re-frame/dispatch [:search-error %])})
   db)

(re-frame/register-handler :search search)

(defn get-document [db [_ id]]
  (GET "http://localhost:3000/solr/frontpage/select"
       {:format :json
        :response-format :json
        :keywords? true
        :params {:q (str "id:\"" id "\"") :wt "json" :fl (solr/create-field-param solr/document-fields)}
        :handler #(re-frame/dispatch [:get-document-result %])
        :error-handler #(re-frame/dispatch [:search-error %])})
  db)

(re-frame/register-handler :get-document get-document)

(defn search-with-text [db [_ query-text]]
  "Search with a new query from the text box"
  (if-not (string/blank? query-text)
    (let [db (assoc db :search-params (merge default-search-params {:text query-text}))]
      (re-frame/dispatch [:search])
      db)
    (assoc-in db [:search-params :text] "")))

(re-frame/register-handler :search-with-text
                           [(db/validate [:search-params] db/SearchParams)]
                           search-with-text)

(defn search-with-field [db [_ [field-name field-value]]]
  (let [db (if (string/blank? field-value)
             (update-in db [:search-params] dissoc :fields)
             (-> db
                 (assoc-in [:search-params :fields] {field-name field-value})
                 (assoc-in [:search-params :page] 0)))]
    (re-frame/dispatch [:search])
    db))

(re-frame/register-handler :search-with-field
                           [(db/validate [:search-params] db/SearchParams)]
                           search-with-field)

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
                (s/one solr/SolrFacetFields nil)]))

(defn search-result [db [_ solr-response]]
  "Translate a Solr search response and store it in the db under :search-result"
  (let [response (to-kebab-case-keyword solr-response)
        docs (solr/extract-docs response)
        nof-found (get-in response [:response :num-found] 0)
        page-size (get-in db [:search-params :page-size])
        facets-ungrouped (get-in response [:facet-counts :facet-fields])
        facets (update-in facets-ungrouped [:author] #(partition 2 %))] ; group the facet counts into pairs
    (-> db
        (assoc :search-result [:search-items docs nof-found facets])
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

(defn to-kebab-case-keyword [x]
  (letfn [(convert-kv [[k v]]
            (if (keyword? k) [(csk/->kebab-case-keyword k) v] [k v]))
          (convert-map [x]
            (if (map? x) (into {} (map convert-kv x)) x))]
    (walk/postwalk convert-map x)))

(re-frame/register-handler
 :toggle-debug
 (fn [db _]
   (assoc db :debug (not (:debug db)))))
