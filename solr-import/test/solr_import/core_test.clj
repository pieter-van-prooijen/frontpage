(ns solr-import.core-test
  (:require [clojure.test :refer :all]
            [solr-import.core :refer :all]))

(deftest collapse-same-test
  (testing "nil-punning"
    (is (= nil (collapse-same nil)))
    (is (= '() (collapse-same '()))))
  (testing "numbers and nils"
    (is (= '(1 2 3) (collapse-same '(1 2 2 3 3 3))))
    (is (= '(1 2 3) (collapse-same '(1 2 2 3))))
    (is (= '(1 2 nil 3) (collapse-same '(1 2 2 nil nil 3 3 3))))
    (is (= '(1 2 nil) (collapse-same '(1 2 2 nil))))
    (is (= '(nil) (collapse-same '(nil))))))

