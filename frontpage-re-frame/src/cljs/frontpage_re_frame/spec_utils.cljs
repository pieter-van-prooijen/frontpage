(ns frontpage-re-frame.spec-utils
  "Common predicates for numbers, strings etc."
  (:require [cljs.spec.alpha :as s]
            [clojure.string :as string]))

;; Integers
(s/def ::pos-int (s/and integer? pos?))
(s/def ::zero-or-pos-int (s/and integer? (s/or :zero zero? :positive pos?)))

;; Strings
(s/def ::non-blank (s/and string? (comp not string/blank?)))

;; Dates and times
(s/def ::date-time (partial instance? js/Date))

;; Sets
(defn set-of [member-spec]
  (s/coll-of member-spec :kind set?))

;; Throw an error in case of an invalid value
;; TODO: use official spec function ?
(defn spec-validate [spec value]
  (if (s/valid? spec value)
    value
    (throw (js/Error. (s/explain-str spec value)))))

