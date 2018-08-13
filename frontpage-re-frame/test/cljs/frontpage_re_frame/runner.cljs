(ns frontpage-re-frame.runner
  (:require [doo.runner :refer-macros [doo-tests doo-all-tests]]
            [frontpage-re-frame.solr-test]
            [frontpage-re-frame.handlers.core-test]
            [frontpage-re-frame.spec-utils-test]
            [frontpage-re-frame.db-test]))

#_(doo-all-tests)

(def all-tests-fn
  (doo-tests 'frontpage-re-frame.spec-utils-test
             'frontpage-re-frame.solr-test
             'frontpage-re-frame.handlers.core-test
             'frontpage-re-frame.db-test))

;; Run the doo tests from the repl
(defn all-tests []
  (with-redefs
    [doo.runner/*exit-fn* identity]
    (all-tests-fn)))


all-tests-fn


