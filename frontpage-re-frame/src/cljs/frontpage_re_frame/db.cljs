(ns frontpage-re-frame.db
  (:require[cljs.spec :as spec]
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

;; Throw an error in case of an invalid value
;; TODO: use official spec function or put in spec-utils
(defn spec-validate [spec value]
  (if (spec/valid? spec value)
    value
    (throw (js/Error. (spec/explain-str spec value)))))

(defn validate [keys spec-or-validator]
  "Handler middleware factory for validating parts of the db after the main handler has run
   Use as [(validate keys schema-or-validator] in the 2nd argument of the 3-arity register-handler function"
  (fn [handler]
    (fn [db v]
      (let [new-db (handler db v)
            value (get-in new-db keys)]
        (if (fn? spec-or-validator )
          (spec-or-validator value)
          (spec-validate spec-or-validator value))
        new-db))))

(def default-db {})
