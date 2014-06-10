(ns frontpage-client.pagination
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(enable-console-print!)

(defn- goto-page-attr [app owner page page-changed-fn]
  "Answer a map with an onClick handler for the designated page"
  {:onClick (fn [_]
              (om/update! app :page page)
              (page-changed-fn page)
              false)})

(defn- page-li 
  ([app owner page-changed-fn page-num class]
      (page-li app owner page-changed-fn page-num class nil))
  ([app owner page-changed-fn page-num class html-str]
     (dom/li #js {:className class}
              (if html-str
                (frontpage-client.util/html-dangerously
                 dom/a (goto-page-attr app owner page-num page-changed-fn) html-str)
                (dom/a (clj->js (goto-page-attr app owner page-num page-changed-fn)) (inc page-num))))))

(defn pagination [app owner opts]
  "Build a pagination component which requires three keys in its state:
   page (current, zero-based page number), nof-docs and page-size.
   Takes a page-changed-fn key in its opts parameter which is called with a singe page parameter"
  (reify
    om/IRender
    (render [this]
      (apply dom/ul #js {:className "pagination"}
             (let [nof-pages (inc (int (/ (:nof-docs app) (:page-size app))))
                   page-changed-fn (:page-changed-fn opts)
                   page-li-part (partial page-li app owner page-changed-fn)] ; fix the first three args
               (concat
                [(page-li-part 0 "arrow" "&laquo;")]
                (let [page (:page app)
                      start-page-nums (range 0 3) ; include possible ellipsis at 2
                      middle-page-nums (range (dec page) (+ page 2))
                      end-page-nums (range (- nof-pages 3) nof-pages) ; include possible ellipsis at -2
                      numbers-seq (distinct (filter #(and (>= % 0) (< % nof-pages))
                                                    (concat start-page-nums middle-page-nums end-page-nums)))
                      ;; Don't show the ellipsis if the middle page range overlaps.
                      ellipsis (apply disj #{2 (- nof-pages 3)} middle-page-nums)]
                  (for [page-num (sort numbers-seq)]
                    (if (= page-num page)
                      (page-li-part page-num "current")
                      (if (ellipsis page-num) 
                        (page-li-part page-num "unavailable" "&hellip;")
                        (page-li-part page-num "")))))
                [(page-li-part (dec nof-pages) "arrow" "&raquo;")]))))))
