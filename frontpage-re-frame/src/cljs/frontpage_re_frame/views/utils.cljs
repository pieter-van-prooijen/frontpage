(ns frontpage-re-frame.views.utils
  (:require [re-frame.core :as re-frame]))

;; Subscribe and deref in one go
(def <sub (comp deref re-frame/subscribe))
(def >evt re-frame/dispatch)
