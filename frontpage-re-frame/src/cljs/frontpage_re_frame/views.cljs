(ns frontpage-re-frame.views
  (:require [cljs.pprint :as pprint]
            [re-frame.core :as re-frame]
            [re-frame.db]
            [reagent.core :as reagent]
            [goog.i18n.DateTimeFormat]
            [goog.dom]
            [jayq.core :refer [$]]
            [clojure.string :as string]))

(declare search-result-item self-opening-reveal)

(def printable-date-time-format (goog.i18n.DateTimeFormat. "yyyy-MM-dd hh:mm"))

;; Appstate debugger
(defn view-app-state []
  (let [debug (re-frame/subscribe [:debug])]
    (fn []
      [:div.row
       [:div.small-12.column
        [:a {:on-click (fn [e]
                         (.preventDefault e)
                         (re-frame/dispatch [:toggle-debug]))} (if @debug "hide app-state" "show app-state")]
        (when @debug
          [:pre (with-out-str (pprint/pprint @re-frame.db/app-db))])]])))

(defn search-box []
  [:form.row {:on-submit (fn [e] (.preventDefault e)
                           (re-frame/dispatch [:search-with-text
                                               (.-value (goog.dom/$ "search-text"))]))}
   [:div.small-4.column
    [:div.input-group
     [:input.input-group-field {:id "search-text" :type "text" :placeholder "Search for..."}]
     [:div.input-group-button
      [:input.button {:type "submit" :value "Search"}]]]
    [:div.small-8.column " "]]])

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

    ;; remove duplicate ellipsis which happen if current is at the start or end.
    (->> (for [page-num (sort numbers-seq)]
           (if (ellipsis page-num) -1 page-num))
         (partition-by identity)
         (map first))))

(defn on-click-attr [page]
  {:href "#"
   :on-click (fn [e]
               (.preventDefault e)
               (re-frame/dispatch [:search-with-page page]))}) 

(defn pagination []
  (let [search-params (re-frame/subscribe [:search-params])]
    (fn []
      (let [{:keys [page nof-pages]} @search-params
            page-nums (build-page-nums nof-pages page)]
        
        [:ul.pagination {:role "navigation"}
        
         (if (zero? page)
           [:li.pagination-previous.disabled {:key 0} "Previous"]
           [:li.pagination-previous {:key 0}
            [:a (on-click-attr (dec page)) "Previous"]])

         ;; Can't use page-nums as a react key because of duplicate -1 entries.
         (for [[item key] (map vector page-nums (map inc (range)))] 
           (if (= item page)
             [:li.current {:key key} (inc page)]
             (if (neg? item)
               [:li {:class "ellipsis" :key key}]
               [:li {:key key} [:a (on-click-attr item) (inc item)]])))
         
         (let [last-key (+ (count page-nums) 2)]
           (if (= page (dec nof-pages))
             [:li.pagination-previous.disabled {:key last-key} "Next"]
             [:li.pagination-next {:key last-key}
              [:a (on-click-attr (inc page)) "Next"]]))])))) 
 

(defn author-list [facets]
  (let [authors (:author facets)]
    [:div
     [:h4 "Authors"]
     [:ul
      (for [[author nof-docs] authors]
        [:li {:key author}
         [:a {:on-click (fn [e]
                          (.preventDefault e)
                          (re-frame/dispatch
                           [:search-with-field [:author (if (= (count authors) 1) nil author)]]))}
          author]
         [:span " (" nof-docs ")"]])]]))


;; Multi method which dispatches on the type of result of the vector under :search-result
(defmulti search-result first)

(defmethod search-result :search-items [[_ items nof-found facets]]
  [:div.row
   [:div.small-3.column
    [author-list facets]]
   [:div.small-9.column
    [:div.row.search-result-count
     [:div.small-12.column
      [:b (str nof-found (if (= nof-found 1) " item" " items") " found.")]]]
    [:div.row
     [:div.small-12.column
      [pagination]]]
    [:div.row
     [:div.small-12.column
      (for [item items]
        ;; search-result-item is a 2n form component, use metadata on the component to set the key
        ^{:key (:id item)} [search-result-item item])]]
    [:div.row
     [:div.small-12.column
      [pagination]]]
    [:div.row
     [:div.small-12.column]]]])

(defn search-result-item [item]
  (let [id (:id item)
        doc (re-frame/subscribe [:document-result id])]
    (fn [item]
      [:div {:class "search-result-item"}
       [:h3
        [:a {:href "#" :on-click (fn [e]
                                   (re-frame/dispatch [:get-document (:id item)])
                                   (.preventDefault e)) }
         (:title  item)]]

       [:div 
        (when @doc
          (let [reveal-id (str "reveal-" id)]
            [self-opening-reveal
             reveal-id
             (fn []
               (println "Rendering document...")
               [:div.document
                [:div {:dangerouslySetInnerHTML {:__html (:body @doc)}}]
                [:span.button {:on-click (fn [_] (.foundation ($ (str "#" reveal-id)) "close"))} "Close"]])
             (fn [e]
               (re-frame/dispatch [:remove-document-result]))]))]
       
       [:p {:dangerouslySetInnerHTML {:__html (:highlight item)}}]
       
       [:div {:class "metadata"}
        [:span (:author item)]
        " | "
        [:span (string/join ", " (:categories item))]
        " | "
        [:span (.format printable-date-time-format (:created-on item))]]])))

;; render-fn should render the content of the reveal
;; closed-fn should trigger an event which removes the reveal from the virtual dom so
;; it is in sync with the real dom after closing the reveal.
(defn self-opening-reveal [id render-fn closed-fn]
  (letfn [(open []
            (-> ($ (str "#" id))
                (.foundation) ; initialize any plugins on the new html
                (.foundation "open")
                (.on "closed.zf.reveal" closed-fn))
            (println "Opening..." (goog.dom/$ id))
            (reagent/render [render-fn]  (goog.dom/$ id)))
          (close []
            (println "Closing..")
            (goog.dom/removeNode (goog.dom/getElement id)))] ; force an update of the DOM
    (reagent/create-class
     {:reagent-render (fn [id _ _]
                        ;; this piece of the dom is moved by Foundation, don't let react see it
                        (println "Rendering reveal...")
                        [:div {:dangerouslySetInnerHTML {:__html (str "<div id='" id "' class='small reveal' data-reveal></div>")}}])
      :component-did-mount open
      :component-will-unmount close})))

(defmethod search-result :search-error [[_ error]]
  [:div.row
   [:div.small-12.column
    [:div.callout.alert error]]])

(defmethod search-result :loading [[_ _]]
  [:div.row
   [:div.small-12.column
    [:div.callout.primary "Loading..."]]])

(defmethod search-result :default [_]
  [:div.row
   [:div.small-3.column]
   [:div.small-9.column]])

(def search-param
  {:page 0 :nof-pages 2 :page-size 10})


(defn main-panel []
  (let [result (re-frame/subscribe [:search-result])]
    (fn []
      [:div
       [view-app-state]
       [:div.row
        [:div.small-3.column]
        [:div.small-9.column
         [:h1 "Re-frame Solr"]]]
       [:div.row
        [:div.small-3.column]
        [:div.small-9.column
         [search-box]]]
       [search-result @result]])))


