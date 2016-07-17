(ns frontpage-re-frame.db-test
  (:require [frontpage-re-frame.db :as db]
            [clojure.string :as string]
            [cljs.spec :as s]
            [frontpage-re-frame.spec-utils :as spec-utils]
            [cljs.test :refer-macros [deftest testing is]]))

(deftest spec
  (testing "spec-validate"
    (try
      (db/spec-validate pos? -1)
      (= true "should throw an error")
      (catch js/Error e
        (is (pos? (string/index-of (str e) "fails predicate")))))))

(deftest update-fields-parameter
  (let [db {:search-params {:fields {:a #{"a" "a1"} :b #{"b"}}}}]
    (testing "adding"
      (is (= (db/update-fields-parameter db :a "a2" [:b] false) {:search-params {:fields {:a #{"a" "a1" "a2"} :b #{"b"}}}})))

    (testing "remove with children"
      (is (= (db/update-fields-parameter db :a "a" [:b] true) {:search-params {:fields {:a #{"a1"} :b #{}}}})))))

