(ns frontpage-re-frame.db
  (:require[cljs.spec.alpha :as spec]
           [frontpage-re-frame.spec-utils :as spec-utils]
           [re-frame.core :as re-frame]))

(spec/def ::page ::spec-utils/zero-or-pos-int)
(spec/def ::page-size ::spec-utils/pos-int)
(spec/def ::nof-pages ::spec-utils/zero-or-pos-int)
(spec/def ::text string?)


(spec/def ::fields (spec/map-of keyword? (spec-utils/set-of (spec/or :number number? :string string?))))

(spec/def ::search-params (spec/keys :req-un [::page ::page-size ::nof-pages ::text] :opt-un [::fields]))

(defn update-fields-parameter [db field value children remove?]
  (as-> db current-db
    (update-in current-db [:search-params :fields] (fn [fields]
                                                     (if remove?
                                                       (update-in fields [field] (fn [values] (disj values value)))
                                                       (update-in fields [field] (fn [values]
                                                                                   (conj (or values #{}) value))))))
    ;; removing a value from a parent field resets all chilren fields.
    (if remove?
      (reduce (fn [db child-field]
                (assoc-in db [:search-params :fields child-field] #{})) current-db children)
      current-db)))


;; factory for creating a validation interceptor on the database
(defn validate [path spec-or-validator]
  (re-frame/after
   (fn [db _]
     (when ^boolean js/goog.DEBUG
       (let [value (get-in db path)]
         (if (fn? spec-or-validator )
           (spec-or-validator value)
           (spec-utils/spec-validate spec-or-validator value)))))))

(def default-search-params  {:page 0 :page-size 10 :nof-pages 0 :text ""})

(def default-db {:search-params default-search-params
                 :debug false})
