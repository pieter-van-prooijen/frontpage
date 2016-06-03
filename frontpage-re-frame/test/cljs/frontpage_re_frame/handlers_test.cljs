(ns frontpage-re-frame.handlers-test
  (:require [frontpage-re-frame.handlers :as handlers]
            [cljs.test :refer-macros [deftest testing is]]))

(deftest handlers
  (testing "converting solr respons"
    (let [r {"camel,Case" {"camelCase" "string"}}]
      (is (= (handlers/to-kebab-case-keyword r) {"camel,Case" {:camel-case "string"}})))))

