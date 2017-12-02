(ns frontpage-re-frame.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :search-result
 (fn [db _]
   (:search-result db)))

(re-frame/reg-sub
 :search-params
 (fn [db _]
   (:search-params db)))

(re-frame/reg-sub
 :document-result
 (fn [db [_ id]]
   "Answer the full document for id or nil, as the current document under :document-result"
   (let [document-result (:document-result db)]
     (when (= (:id document-result) id)
       document-result))))

(re-frame/reg-sub
 :debug
 (fn [db _]
   (:debug db)))
