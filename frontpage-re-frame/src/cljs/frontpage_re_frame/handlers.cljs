(ns frontpage-re-frame.handlers
  (:require [re-frame.core :as re-frame]
            [frontpage-re-frame.db :as db]
            [ajax.core :refer [GET]]
            [schema.core :as s]
            [frontpage-re-frame.solr :as solr]
            [clojure.string :as string]))

(re-frame/register-handler
 :initialize-db
 (fn  [_ _]
   (-> db/default-db
       (assoc :search-params {:page 0 :page-size 10 :nof-pages 0}))))

(re-frame/register-handler
 :search
 (fn [db _]
   (GET "http://localhost:3000/solr/frontpage/select"
        {:format :json
         :response-format :json
         :keywords? true
         :params (solr/search-params (:search-params db))
         :handler #(re-frame/dispatch [:search-result %])
         :error-handler #(re-frame/dispatch [:search-error %])})
   (assoc db :search-result [:loading true])))

(re-frame/register-handler
 :get-document
 (fn [db [_ id]]
   (GET "http://localhost:3000/solr/frontpage/select"
        {:format :json
         :response-format :json
         :keywords? true
         :params {:q (str "id:\"" id "\"") :wt "json" :fl (solr/create-fl-param solr/document-fields)}
         :handler #(re-frame/dispatch [:get-document-result %])
         :error-handler #(re-frame/dispatch [:search-error %])})
   (assoc db :loading true)))

(re-frame/register-handler
 :search-with-text
 [(db/validate [:search-params] db/SearchParams)]
 (fn [db [_ query-text]]
   (if-not (string/blank? query-text)
     (let [db (assoc-in db [:search-params :text] query-text)]
       (re-frame/dispatch [:search])
       db)
     (assoc-in db [:search-params :text] ""))))

(re-frame/register-handler
 :search-with-page
 [(db/validate [:search-params] db/SearchParams)]
 (fn [db [_ page]]
   (let [db (assoc-in db [:search-params :page] page)]
     (re-frame/dispatch [:search])
     db)))

(def search-result-validator
  (s/validator [(s/one (s/eq :search-items) "type") (s/one [solr/SolrDocument] "documents") (s/one s/Num "nof-documents")]))

(re-frame/register-handler
 :search-result
 [(db/validate [:search-result] search-result-validator)]
 (fn [db [_ solr-response]]
   (let [docs (solr/extract-docs solr-response)
         nof-found (get-in solr-response [:response :numFound] 0)
         page-size (get-in db [:search-params :page-size])]
     (-> db
         (assoc :search-result [:search-items docs nof-found])
         (assoc-in [:search-params :nof-pages] (js/Math.ceil (/ nof-found page-size)))))))

(re-frame/register-handler
 :search-error
 [(db/validate [:search-result] [(s/one (s/eq :search-error) "type") (s/one s/Str "error-str")])]
 (fn [db [_ response]]
   (let [msg (get-in response [:response :error :msg] (:status-text response))]
     (assoc db :search-result [:search-error msg]))))

(re-frame/register-handler
 :get-document-result
 [(db/validate [:document-result] solr/SolrDocument)]
 (fn [db [_ solr-response]]
   (let [docs (solr/extract-docs solr-response)]
     (if (= (count docs) 1)
       (assoc db :document-result (first docs))
       db))))

(re-frame/register-handler
 :remove-document-result
 (fn [db _]
   (dissoc db :document-result)))
