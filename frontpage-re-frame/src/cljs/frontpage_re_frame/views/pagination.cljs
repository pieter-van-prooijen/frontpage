(ns frontpage-re-frame.views.pagination
  (:require [re-frame.core :as re-frame]
            [frontpage-re-frame.views.utils :refer [<sub >evt]]))

(defn build-page-nums [nof-pages page]
  "Build a list of page-nums to display, where -1 indicates an ellipsis
   List is split in three groups of three page numbers, start, middle-page (current) and end,
   with an optional ellipsis separating the parts if they are far apart."
  (let [start-page-nums (range 0 2)
        middle-page-nums (range (dec page) (+ page 2))
        end-page-nums (range (- nof-pages 2) nof-pages)

        ;; set of pagenumbers which should show an ellipsis
        ;; Don't show the ellipsis if the middle page range is adjacent to or overlaps the start / end ranges
        ellipsis (apply disj #{2 (- nof-pages 3)} (concat start-page-nums middle-page-nums end-page-nums))

        numbers-seq (distinct (filter #(and (>= % 0) (< % nof-pages))
                                      (concat start-page-nums middle-page-nums end-page-nums ellipsis)))]

    ;; remove duplicate ellipsis which occurr if current is at the start or end.
    (->> (for [page-num (sort numbers-seq)]
           (if (ellipsis page-num) -1 page-num))
         (partition-by identity)
         (map first))))

(defn pagination-attributes [page disabled]
  (merge
   {:href "#"
    :on-click (fn [e]
                (.preventDefault e)
                (>evt [:search-with-page page]))}
   (when disabled {:disabled "disabled"}))) 

(defn pagination []
  (let [{:keys [page nof-pages]} (<sub [:search-params])
        page-nums (build-page-nums nof-pages page)]
    
    [:nav.pagination {:role "navigation"}

     [:ul.pagination-list
      
      [:li>:a.pagination-previous (pagination-attributes (dec page) (zero? page)) "Previous"]

      ;; Can't use page-nums as a react key because of duplicate -1 entries.
      (for [[item key] (map vector page-nums (map inc (range)))] 
        (if (= item page)
          [:li {:key key}
           [:a.pagination-link.is-current (inc page)]]
          (if (neg? item)
            [:li {:key key}
             [:a.pagination-ellipsis {:dangerouslySetInnerHTML {:__html "&hellip;"}}]]
            [:li {:key key}
             [:a.pagination-link (pagination-attributes item false) (inc item)]])))
      
      [:li>:a.pagination-next (pagination-attributes (inc page) (= page (dec nof-pages))) "Next"]]])) 

