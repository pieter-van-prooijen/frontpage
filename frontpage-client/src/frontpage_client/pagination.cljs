(ns frontpage-client.pagination
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontpage-client.util :as util]
            [domkm.silk :as silk]))

(util/set-print!)

(defn- goto-page-attr [page page-changed-fn routes]
  "Answer a map with an onClick handler for the designated page,
    which will invoke page-changed-fn with the new page number."
  {:onClick (fn [e]
              (page-changed-fn page)
              (.preventDefault e))
   :href (silk/depart routes :query {:page page})})

(defn- page-li 
  "Create a li element for zero-based page-num containing an optional html-str. Attach the page-changed-fn."
  ([page-changed-fn routes page-num class html-str]
     (dom/li #js {:className class}
             (if html-str
               (frontpage-client.util/html-dangerously
                dom/a (goto-page-attr page-num page-changed-fn routes) html-str)
               (dom/a (clj->js (goto-page-attr page-num page-changed-fn routes)) (inc page-num))))))

(defn build-page-nums [nof-pages page]
  "Build a list of page-nums to display, where -1 indicates an ellipsis
   List is split in three groups of three page numbers, start, middle-page (current) and end,
   with an optional ellipsis separating the parts if they are far apart.
  "
  (let [start-page-nums (range 0 2)
        middle-page-nums (range (dec page) (+ page 2))
        end-page-nums (range (- nof-pages 2) nof-pages)

        ;; set of pagenumbers which should show an ellipsis
        ;; Don't show the ellipsis if the middle page range is adjacent to or overlaps the start / end ranges
        ellipsis (apply disj #{2 (- nof-pages 3)} (concat start-page-nums middle-page-nums end-page-nums))

        numbers-seq (distinct (filter #(and (>= % 0) (< % nof-pages))
                                      (concat start-page-nums middle-page-nums end-page-nums ellipsis)))]
    ;; remove duplicate ellipsis which happen if current is at the start or end. 
    (frontpage-client.util/collapse-same
     (for [page-num (sort numbers-seq)]
       (if (ellipsis page-num) -1 page-num)))))

(def pagination-keys [:page :page-size :nof-docs :page-changed-fn])

(defn pagination [{:keys [page page-size nof-docs page-changed-fn routes]} owner]
  "Build a zurb foundation pagination component which requires three options:
   page (the current, zero-based page number), nof-docs and page-size.
   Takes also a page-changed-fn key in its opts parameter which is called with a singe page parameter
   when the current page changes"
  (om/component
   (apply dom/ul #js {:className "pagination"}
          (let [nof-pages (js/Math.ceil (/ nof-docs page-size))
                page-li-part (partial page-li page-changed-fn routes)] ; fix the first args to page-li
            (concat
             [(page-li-part 0 "arrow" "&laquo;")]
             (for [page-num (build-page-nums nof-pages page)]
               (if (= page-num page)
                 (page-li-part page-num "current" nil)
                 (if (= -1 page-num)    ; ellipsis 
                   (page-li-part page-num "unavailable" "&hellip;")
                   (page-li-part page-num "" nil))))
             [(page-li-part (dec nof-pages) "arrow" "&raquo;")])))))
