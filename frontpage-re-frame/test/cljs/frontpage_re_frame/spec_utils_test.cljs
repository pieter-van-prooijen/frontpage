(ns frontpage-re-frame.spec-utils-test
  (:require [frontpage-re-frame.spec-utils :as spec-utils]
            [cljs.spec :as s]
            [cljs.test :refer-macros [deftest testing is]]))

(deftest spec-utils-set
  (testing "set-of"
    (is (s/valid? (spec-utils/set-of pos?) #{1 2}))
    (is (not (s/valid? (spec-utils/set-of pos?) #{-1}))))) 

(deftest spec-utils-string
  (testing "non-blank"
    (is (s/valid? ::spec-utils/non-blank "a"))
    (is (not (s/valid? ::spec-utils/non-blank "")))
    (is (not (s/valid? ::spec-utils/non-blank " \t")))
    (is (not (s/valid? ::spec-utils/non-blank nil)))))

(deftest spec-utils-date-time
  (testing "date-time"
    (is (s/valid? ::spec-utils/date-time #inst "2016-07-15T12:22:13.746-00:00"))
    (is (not (s/valid? ::spec-utils/date-time nil)))))
