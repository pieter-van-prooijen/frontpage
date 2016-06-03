(ns frontpage-re-frame.solr-test
  (:require [frontpage-re-frame.solr :as solr]
            [frontpage-re-frame.handlers :as handlers]
            [cljs.test :refer-macros [deftest testing is]]
            [schema.core :as s]))
 
(deftest schema
  (testing "document schema validation"
    (let [doc {:id "id" :title "title" :body "body" :author "author" :created-on (js/Date.) :categories #{"cat"}}]
      (is (= (s/validate solr/SolrDocument doc) doc))
      (try
        (s/validate solr/SolrDocument {:foo "bar"})
        (is false "should throw an error")
        (catch js/Error e
          (is true "should throw an error")))))

  (testing "pivot schema validation"
    (let [facet-pivots {:a [{
                             :field "f"
                             :value 3
                             :count 3
                             :pivot [{:field "f1" :value "v" :count 1}]}]}]
      (is (= (s/validate solr/SolrFacetPivots facet-pivots)))))) 

(deftest extracting
  (testing "result extraction"
    (let [response {"responseHeader" {}
                    "response" {"docs" [{"id" "id" "title" "title" "body" "body"
                                       "author" "author" "created_on" (js/Date.) "categories" #{"cat"}}]}}
          response1 (handlers/to-kebab-case-keyword response)
          extracted (solr/extract-docs response1)]
      (is (= 1 (count extracted)))
      (s/validate solr/SolrDocument (first extracted)))))

(deftest test-pivot-facets

  (testing "only top-level"
    (let [toplevel-defs [{:field :a-b} {:field :b-c}]]
      (is (= (solr/create-pivots toplevel-defs {}) '("a_b,a_b" "b_c,b_c")))
      ;; selected fields shouldn't make a difference
      (is (= (solr/create-pivots toplevel-defs {:a-b "foo"}) '("a_b,a_b" "b_c,b_c")))))

  (testing "pivot"
    (let [defs [{:field :a :pivot {:field :b}}]]
      (is (= (solr/create-pivots defs {}) '("a,a")))
      ;; with selection, the pivot should be there
      (is (= (solr/create-pivots defs {:a "foo"}) '("a,b")))))

  (testing "converting the top-level keys of a facet-pivots hash"
    (is (= (solr/convert-pivots {"a_b,b" {:foo "bar"}}) {:a-b {:foo "bar"}}))))

