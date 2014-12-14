(ns frontpage-client.statistics
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [datascript :as d]
            [datascript.core :as dc]
            [frontpage-client.solr :as solr]
            [cljs.core.async :refer [<! >! chan put!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;;
;; Maintain and display document statistics about the frontpage app.


;; prevent cursor-ification
(extend-type dc/DB
  om/IToCursor
  (-to-cursor
    ([this _] this)
    ([this _ _] this)))

(defn create-conn []
  (let [schema {:categories {:db/cardinality :db.cardinality/many }}]
    (d/create-conn schema)))

(defn add-to-ds [conn doc]
  "Register the document"
  (d/transact! conn
               [(assoc (select-keys doc [:author :created-on :categories]) :db/id -1)]))

(defn add-from-solr 
  ([{conn :conn :as app-state} q]
      (let [c (chan)
            batch-size 100]
        (om/update! app-state :processed 0)
        (om/update! app-state :total -1)
        (om/update! app-state :stop false)
        (go-loop [batch 0]
          (solr/search q {} batch batch-size {} [:author :created_on :categories] false c)
          (let [result (<! c)
                docs (get-in result [:response :docs])
                with-date (map #(assoc %1 :created-on (js/Date. (:created_on %1))) docs)]
            (when (zero? batch)
              (om/update! app-state :total (get-in result [:response :numFound])))

            (doseq [doc with-date]
              (add-to-ds conn doc)
              (om/transact! app-state :processed inc)))
          (when (and (not (:stop @app-state)) (< (:processed @app-state) (:total @app-state)))
            (recur (inc batch)))))))

(defn list-values [conn attr]
  "List all values which have the attribute."
  (d/q '[:find ?v :in $ ?attr :where [_ ?attr ?v]] @conn attr))

(defn count-docs [conn mode]
  "Count the number of entries which have the specified mode."
  (let [result (d/q '[:find (count ?e) :in $ ?m :where [?e :mode ?m]] @conn mode)]
    (if (seq result) (first (first result)) 0)))

(defn doc-ids [docs]
  "Answer a set of all the ids in docs"
  (set (map :id docs)))
 
(defn remove-child-nodes [id]
  (when-let [n (.getElementById js/document id)]
    (while (.hasChildNodes n)
      (.removeChild n (.-lastChild n)))))

;; Data is a sequence of {:category a :count n} (or empty)
(defn draw-chart [id x-key y-key data]
  (when (not-empty data)
    (let [svg (js/dimple.newSvg (str "#" id) "100%" 400)
          js-data (clj->js data)
          chart (js/dimple.chart. svg js-data)]
      (doto chart 
        (.addCategoryAxis "x" (name x-key))
        (.addMeasureAxis "y" (name y-key))
        (.addSeries nil js/dimple.plot.bar) 
        (.draw)))))

(defmulti get-data (fn [view conn] view))

(defmethod get-data :category-count [_ conn]
  (let [ds-query '[:find ?category (count ?doc) :where [?doc :categories ?category]]
        data (->> (d/q ds-query @conn)
                  (map (fn [[category count]] {:category category :count count}))
                  (remove #(= (:category %1) "uncategorized")))]
    [:category :count data]))

(defmethod get-data :author-count [_ conn]
  (let [ds-query '[:find ?author (count ?doc) :where [?doc :author ?author]]
        data (->> (d/q ds-query @conn)
                  (map (fn [[author count]] {:author author :count count})))]
    [:author :count data]))

(defn views-menu [[view :as view-v] owner]
  (om/component
   (html [:div.row
          [:div.large-12.columns.end
           [:dl.sub-nav
            [:dt "Views:"]
            (for [k [:category-count :author-count]]
              [:dd {:key (name k)
                    :class (when (= k view) "active")}
               [:a {:on-click #(om/update! view-v [k])} (str (name k))]])]]])))

(defn root [{:keys [conn processed total view] :as app-state} owner {:keys [q]}]
  (let [id "chart"
        [x-key y-key data] (get-data (first view) conn)]
    (reify
      om/IWillMount
      (will-mount [_]
        (when-not (clojure.string/blank? q)
          (add-from-solr app-state q))) ; needs to update the state.
      om/IRender
      (render [_]
        (html [:div
               (om/build views-menu view)
               [:div.row
                     [:div.large-12.columns.end
                      [:div#chart]]]
               [:div.row
                [:div.large-12.columns.end
                 [:span (if (neg? total) "Fetching..." (str "Processing: " processed " / " total))]]]
               [:div.row
                [:div.large-2.columns [:span ""]
                 [:a.button {:on-click #(om/update! app-state :conn (create-conn)) } "Clear"]]
                [:div.large-1.columns ""]
                [:div.large-2.columns.end
                 [:a.button {:on-click #(om/update! app-state :stop true)} "Stop" ]]]]))
      om/IDidMount
      (did-mount [_]
        (draw-chart id x-key y-key data))
      om/IDidUpdate
      (did-update [_ _ _]
        (remove-child-nodes id)
        (draw-chart id x-key y-key data)))))

(defonce app-state (atom {:conn (create-conn)
                          :processed 0
                          :total 0
                          :view [:category-count] ; must be a cursor.
                          :stop false}))

(defn mount-root [q state]
  (om/root root state {:opts  {:q q}
                       :target (. js/document (getElementById "app"))}))


; for figwheel, already done by secretary
(mount-root "foobar" app-state)




 




 
