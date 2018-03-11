(ns frontpage-re-frame.handlers.editable
  (:require [re-frame.core :as re-frame]
            [frontpage-re-frame.db :as db]
            [ajax.core :as ajax]
            [frontpage-re-frame.solr :as solr]))

(re-frame/reg-event-db
 :update-document-field
 (fn [db [_ field value]]
   (update-in db [:document-result field] (constantly value))))

(re-frame/reg-fx
 :http-post
 (fn [{:keys [url params success-event failure-event]}]
   (ajax/POST url {:format :json
                   :response-format :json
                   :keyword? false ; handled by from-kebab-keyword
                   :params params
                   :handler #(re-frame/dispatch [success-event %])
                   :error-handler #(re-frame/dispatch [failure-event %])
                   :vec-strategy :java})))

(re-frame/reg-event-fx
 :update-document
 [(db/validate [:document-result] ::solr/document)]
 (fn [{db :db} _]
   ;; TODO: extract text from html, remove non-solr schema fields
   (let [doc (solr/from-kebab-case-keyword (:document-result db))]
     {:http-post {:url "/solr/frontpage/update"
                  :params {:add {:doc doc
                                 :overwrite true}}
                  :success-event :edit-document-result-success
                  :failure-event :search-error}})))

(re-frame/reg-event-db
 :edit-document-result
 (fn [db _]
   (-> db
       (assoc :original-document-result (:document-result db))
       (update-in [:document-result :edit] (constantly true)))))

(re-frame/reg-event-db
 :edit-document-result-success
 (fn [db _]
   (-> db
       (assoc :search-result [:search-msg "Updated document"])
       (dissoc :document-result)
       (dissoc :original-document-result))))

(re-frame/reg-event-db
 :unedit-document-result
 (fn [db _]
   (-> db
       (assoc :document-result (:original-document-result db))
       (dissoc :original-document-result))))
