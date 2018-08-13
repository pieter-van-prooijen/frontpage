(ns frontpage-re-frame.handlers.core-test
  (:require [frontpage-re-frame.handlers.core :as handlers]
            [cljs.test :refer-macros [deftest testing is]]
            [re-frame.core :as rf]
            [day8.re-frame.test :as rf-test]
            [re-frame.db]
            [ajax.core :as ajax]))

(def solr-response
  {"response"
   {"docs" [{"id" "some-id"
             "title" "some title"
             "author" "some author"
             "created_on" "1999-01-02T12:34:56Z"
             "categories" []
             "created_on_year" 1999
             "created_on_month" 1
             "created_on_day" 2}]}
   "highlighting" {"some-id" {"text" "some highlighted text"}}})

(deftest search-with-text  
  (rf-test/run-test-sync
   ;; stub out the http call
   (rf/reg-fx
    :http
    (fn [{:keys [success-event]}]
      (rf/dispatch [success-event solr-response])))
   
   (rf/dispatch [:initialize-db])

   ;; test
   (rf/dispatch [:search-with-text "foo-bar"])
   
   (print re-frame.db/app-db)
   (is (= (get-in @re-frame.db/app-db [:search-params :text]) "foo-bar"))))


