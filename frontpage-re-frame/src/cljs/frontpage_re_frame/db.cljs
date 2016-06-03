(ns frontpage-re-frame.db
  (:require [schema.core :as s]
            [re-frame.core :as re-frame]))

(def SearchParams
  "schema for the current search parameters in the database"
  {:page s/Num
   :page-size s/Num
   :nof-pages s/Num
   :text s/Str
   (s/optional-key :fields) {s/Keyword [(s/cond-pre s/Str s/Int)]}
})

(defn update-fields-parameter [db field value remove?]
  (update-in db [:search-params :fields] (fn [fields]
                                           (if remove?
                                             (update-in fields [field] (fn [values] (remove (partial = value) values)))
                                             (update-in fields [field] (fn [values] (conj values value)))))))

(defn validate [keys schema-or-validator]
  "Handler middleware factory for validating parts of the db after the main handler has run
   Use as [(validate keys schema-or-validator] in the 2nd argument of the 3-arity register-handler function"
  (fn [handler]
    (fn [db v]
      (let [new-db (handler db v)]
        (if (fn? schema-or-validator )
          (schema-or-validator (get-in new-db keys))
          (s/validate schema-or-validator (get-in new-db keys)))
        new-db))))

(def default-db {})
