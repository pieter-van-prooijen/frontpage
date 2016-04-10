(ns frontpage-re-frame.runner
    (:require [doo.runner :refer-macros [doo-tests]]
              [frontpage-re-frame.core-test]))

(doo-tests 'frontpage-re-frame.core-test)
