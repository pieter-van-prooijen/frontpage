(ns frontpage-re-frame.views.core
  (:require [cljs.pprint :as pprint]
            [re-frame.core :as re-frame]
            [re-frame.db]
            [reagent.core :as reagent]
            [goog.i18n.DateTimeFormat]
            [goog.i18n.NumberFormat]
            [goog.date.Date]
            [goog.dom]
            [clojure.string :as string]
            [frontpage-re-frame.solr :as solr]
            [frontpage-re-frame.views.pagination :as pagination]
            [frontpage-re-frame.views.editable :as editable]
            [frontpage-re-frame.views.utils :refer [<sub >evt]]))

(declare search-result-item self-opening-reveal)

(def printable-date-time-format (goog.i18n.DateTimeFormat. "yyyy-MM-dd hh:mm"))
(def printable-number-format (goog.i18n.NumberFormat. goog.i18n.NumberFormat/Format.DECIMAL))

;; Appstate debugger
(defn view-app-state []
  (let [debug (<sub [:debug])]
    [:div
     [:a {:on-click (fn [e]
                      (.preventDefault e)
                      (>evt [:toggle-debug]))} (if debug "hide app-state" "show app-state")]
     (when debug
       [:pre (with-out-str (pprint/pprint @re-frame.db/app-db))])]))

(defn search-box []
  [:form {:on-submit (fn [e]
                       (.preventDefault e)
                       (>evt [:search-with-text
                              (.-value (goog.dom/getElement "search-text"))]))}
   [:div.field.has-addons
    [:div.control
     [:input.input {:id "search-text" :type "text" :placeholder "Search for..."}]]
    [:div.control
     [:input.button.is-primary {:type "submit" :value "Search"}]]]])

(defn facet-list [facet-definition facets]
  (let [search-params (<sub [:search-params])
        {:keys [:field :title :level]} facet-definition
        facet-pivots (get facets field)]
    (when (pos? (count facet-pivots))
      [:div.facet-list
       [(keyword (str "h" (+ level 4) ".subtitle")) title]
       [:ul
        (for [{facet-value :value nof-docs :count pivot :pivot} (take 10 facet-pivots)]
          (let [values (get-in search-params [:fields field])
                active?  (some (partial = facet-value) values)]
            [:li {:key facet-value}
             [:a {:on-click (fn [e]
                              (.preventDefault e)
                              (>evt [:search-with-fields [[field facet-value active?]] false]))
                  :style (when active? {:font-weight "bold"})}
              facet-value]
             [:span " (" nof-docs ")"]
             (when-let [child-facet-definition (and active? (first (:pivot facet-definition)))]
               [facet-list child-facet-definition {(:field child-facet-definition) pivot}])]))]])))

;; Show the facets of a search-items result
(defn facet-result []
  (let [[_ _ nof-found facets] (<sub [:search-result])]
    [:div
     (when (pos? nof-found)
       [:a.is-hidden-tablet {:href "#top"} "top"])
     (for [facet-definition solr/facet-definitions]
       ^{:key (:field facet-definition)} [facet-list facet-definition facets])]))

;; Multi method which dispatches on the type of result of the vector under :search-result
(defmulti search-result first)

(defmethod search-result :search-items [[_ items nof-found facets]]
  [:div
   [:div.columns.is-mobile.search-result-count
    [:div.column
     [:b (str (.format printable-number-format nof-found) (if (= nof-found 1) " item" " items") " found." )]]
    [:div.colum
     (when (pos? nof-found)
       [:a.is-hidden-tablet {:href "#facets"} "facets"])]]
   [pagination/pagination]
   [:div
    (for [item items]
      ;; search-result-item is a 2nd form component, use metadata on the component to set the key
      ^{:key (:id item)} [search-result-item item])]
   [pagination/pagination]])

(defn field-link [field value]
  [:a {:href "#" :key value :on-click (fn [e]
                                        (.preventDefault e)
                                        (>evt [:search-with-fields [[field value false]] false]))} value])

(defn date-link [js-date]
  (let [date (goog.date.Date. js-date)]
    [:a {:href "#" :on-click (fn [e]
                               (.preventDefault e)
                               (let [year (.getFullYear date)
                                     month (inc (.getMonth date))
                                     day (.getDate date)]
                                 (>evt [:search-with-fields [[:created-on-year year false]
                                                             [:created-on-month month false]
                                                             [:created-on-day day false]]
                                        true])))}
     (.format printable-date-time-format js-date)]))

(defn edit-button []
  [:a {:href "#" :on-click (fn [e]
                             (.preventDefault e)
                             (>evt [:edit-document-result]))}
   "edit"])

(defn document-inline [_ doc]
  (if (:edit doc)
    [editable/editable-document doc]
    [:div
     [:div
      [edit-button]]
     [:div.content {:dangerouslySetInnerHTML {:__html (:body doc)}}]
     [:span.button.is-primary {:on-click (fn [_] (>evt [:remove-document-result]))} "Close"]]))

(defn document-modal [_ doc]
  [:div.modal.is-active
   [:div.modal-background {:on-click (fn [e]
                                       (>evt [:remove-document-result]))}]
   [:div.modal-card
    [:header.modal-card-head
     [:p.modal-card-title (:title doc)] 
     [:button.delete {:on-click (fn [e]
                                  (>evt [:remove-document-result]))}]]
    [:div.modal-card-body
     (if (:edit doc)
       [editable/editable-document doc]
       [:div
        [:div
         [edit-button]]
        [:div.content {:dangerouslySetInnerHTML {:__html (:body doc)}}]])]]
   [:button.modal-close]])

(defn search-result-item [item]
  (let [id (:id item)
        doc (<sub [:document-result id])]

    [:div.search-result-item
     [:h1.subtitle
      [:a {:href "#" :on-click (fn [e]
                                 (.preventDefault e)
                                 (if doc
                                   (>evt [:remove-document-result])
                                   (>evt [:get-document (:id item)]))) }
       (:title item)]]

     (if doc
       (document-inline id doc)
       #_(document-modal id doc)
       [:p.extract {:dangerouslySetInnerHTML {:__html (:highlight item)}}]) ;; render the Solr highlight tags
     
     [:div
      [field-link :author (:author item)]
      " | "
      [:span (interpose
              ", "
              (map #(field-link :categories %)  (:categories item)))]
      " | "
      [date-link (:created-on item)]]]))

(defmethod search-result :search-error [[_ error]]
  [:div.notification.is-danger error])

(defmethod search-result :search-msg [[_ msg]]
  [:div.notification.is-info msg])

(defmethod search-result :loading [[_ _]]
  [:div.notification.is-info "Loading..."])

(defmethod search-result :default [_]
  [:div])

(defn search-result-container []
  [search-result (<sub [:search-result])])

(def search-param
  {:page 0 :nof-pages 2 :page-size 10})


(defn empty-column []
  [:div.column.is-3 {:style "height: 0px"} ""])

(defn row
  "Define a row of layout with two columns, main content and side content. Main content is displayed first in mobile"
  ([main side-anchor side]
   [:div.columns
    (if (= side :empty-column)
      [:div.column.is-3.hidden-mobile.zero-height ""]
      [:div.column.is-3.is-hidden-mobile side])
    [:div.column.is-9 main]
    (when side-anchor
      [:div.column.is-3.is-hidden-tablet side-anchor])
    (if (= side :empty-column)
      [:div.column.is-3.hidden-tablet.zero-height ""]
      [:div.column.is-3.is-hidden-tablet side])])
  ([main]
   (row main nil :empty-column)))

(defn main-panel []
  [:div.container
   [row
    [:a {:name "top"}]]
   [row
    [view-app-state]]
   [row
    [:h1.title "Re-frame + Solr"]]
   [row
    [search-box]]
   [row
    [search-result-container]
    [:a {:name "facets"}]
    [facet-result]]])
