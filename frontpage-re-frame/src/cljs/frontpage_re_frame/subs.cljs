(ns frontpage-re-frame.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]))

(re-frame/register-sub
 :search-result
 (fn [db _]
   (reaction (:search-result @db))))

(re-frame/register-sub
 :facet-result)
(re-frame/register-sub
 :search-params
 (fn [db _]
   (reaction (:search-params @db))))

(re-frame/register-sub
 :document-result
 (fn [db [_ id]]
   "Answer the full document for id or nil, as the current document under :document-result"
   (let [document-result (reaction (:document-result @db))]
     (reaction (when (= (:id @document-result) id)
                 @document-result)))))

(re-frame/register-sub
 :debug
 (fn [db _]
   (reaction (:debug @db))))
