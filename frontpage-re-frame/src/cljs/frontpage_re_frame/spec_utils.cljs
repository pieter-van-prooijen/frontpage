(ns frontpage-re-frame.spec-utils
  "Common predicates for numbers, strings etc."
  (:require [cljs.spec :as s]
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
  (s/and set? (s/coll-of member-spec #{})))

