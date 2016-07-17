(ns frontpage-re-frame.runner
    (:require [doo.runner :refer-macros [doo-tests doo-all-tests]]
              [frontpage-re-frame.core-test]
              [frontpage-re-frame.solr-test]
              [frontpage-re-frame.handlers-test]
              [frontpage-re-frame.spec-utils-test]
              [frontpage-re-frame.db-test]))

#_(doo-all-tests)

(doo-tests 'frontpage-re-frame.core-test
           'frontpage-re-frame.solr-test
           'frontpage-re-frame.spec-utils-test
           'frontpage-re-frame.handlers-test
           'frontpage-re-frame.db-test)
