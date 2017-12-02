(ns frontpage-re-frame.solr-test
  (:require [frontpage-re-frame.solr :as solr]
            [frontpage-re-frame.handlers :as handlers]
            [cljs.test :refer-macros [deftest testing is]]
            [cljs.spec.alpha :as s]))
 
(deftest schema
  (testing "document schema validation"
    (let [doc {:id "id" :title "title" :body "body" :author "author" :created-on (js/Date.) :categories #{"cat"}}]
      (is (s/valid? ::solr/document doc))
      (is (not (s/valid? ::solr/document {:foo "bar"})))))

  (testing "pivot schema validation"
    (let [facet-pivots {:field :author
                        :value "bla"
                        :count 3
                        :pivot [{:field :created-on-month :value "v" :count 1}]}]
      (is (s/valid? ::solr/facet-pivot facet-pivots))))) 

(deftest extracting
  (testing "result extraction"
    (let [response {"responseHeader" {}
                    "response" {"docs" [{"id" "id" "title" "title" "body" "body"
                                       "author" "author" "created_on" (js/Date.) "categories" #{"cat"}}]}}
          response1 (handlers/to-kebab-case-keyword response)
          extracted (solr/extract-docs response1)]
      (is (= 1 (count extracted)))
      (is (s/valid? ::solr/document (first extracted))))))

(deftest test-pivot-facets

  (testing "only top-level"
    (let [toplevel-defs [{:field :a-b} {:field :b-c}]]
      (is (= (solr/create-pivots toplevel-defs {}) '("a_b,a_b" "b_c,b_c")))
      ;; selected fields shouldn't make a difference
      (is (= (solr/create-pivots toplevel-defs {:a-b "foo"}) '("a_b,a_b" "b_c,b_c")))))

  (testing "pivot"
    (let [defs [{:field :a :pivot [{:field :b}]}]]
      (is (= (solr/create-pivots defs {}) '("a,a")))
      ;; with selection, the pivot should be there
      (is (= (solr/create-pivots defs {:a "foo"}) '("a,b")))))

  (testing "converting the top-level keys of a facet-pivots hash"
    (is (= (solr/convert-pivots {"a_b,b" {:foo "bar"}}) {:a-b {:foo "bar"}})))

  (testing "find the list of all child fields of field"
    (let [defs [{:field :a :pivot [{:field :b}]}]]
      (is (= (solr/child-fields :a defs) '(:a :b)))
      (is (= (solr/child-fields :b defs) '(:b)))
      (is (nil? (solr/child-fields :c defs))))))

 
