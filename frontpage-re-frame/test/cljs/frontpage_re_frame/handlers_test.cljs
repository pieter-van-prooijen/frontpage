(ns frontpage-re-frame.handlers-test
  (:require [frontpage-re-frame.handlers :as handlers]
            [cljs.test :refer-macros [deftest testing is]]))

(deftest handlers
  (testing "converting solr respons"
    (let [r {"camel,Case" {"camelCase" "string"}}]
      (is (= (handlers/to-kebab-case-keyword r) {"camel,Case" {:camel-case "string"}}))))

  (testing "search-with-fields"
    (let [db {:search-params {:fields {:f #{"v"}}}}]
      (is (= (handlers/search-with-fields {:db db} [nil [[:f "v1" false]]])
             (:db {:search-params {:page 0 :fields {:f #{"v" "v1"}}}})))
      ;; add / remove in one operation
      (is (= (handlers/search-with-fields {:db db}
                                          [nil [[:f "v1" false]
                                                [:f "v1" true]]])
             (:db {:search-params {:page 0 :fields {:f #{"v"}}}}))))))


