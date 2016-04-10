(ns frontpage-re-frame.solr-test
  (:require [frontpage-re-frame.solr :as solr]
            [cljs.test :refer-macros [deftest testing is]]
            [schema.core :as s]))
 
(deftest schema
  (testing "schema validation"
    (let [doc {:id "id" :title "title" :body "body" :author "author" :created-on (js/Date.) :categories #{"cat"}}]
      (is (= (s/validate solr/SolrDocument doc) doc))
      (try
        (s/validate solr/SolrDocument {:foo "bar"})
        (is false "should throw an error")
        (catch js/Error e
          (is true "should throw an error")))))) 

(deftest extracting
  (testing "result extraction"
    (let [response {:responseHeader {}
                    :response {:docs [{:id "id" :title "title" :body "body"
                                       :author "author" :created_on (js/Date.) :categories #{"cat"}}]}}
          extracted (solr/extract-docs response)]
      (is (= 1 (count extracted)))
      (s/validate solr/SolrDocument (first extracted)))))
