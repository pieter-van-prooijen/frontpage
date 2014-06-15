(ns frontpage-client.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontpage-client.solr :as solr]
            [cljs.core.async :refer [<! >! take! chan]]
            [frontpage-client.document]
            [frontpage-client.util]
            [frontpage-client.pagination]
            [frontpage-client.facets]
            [frontpage-client.statistics :as statistics]
            [clojure.string]
            [datascript :as d]
            [secretary.core :as secretary :include-macros true])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(defn set-doc [doc id]
  "Replace the specified document with a new doc (using id to retrieve it from solr)."
  (let [c (chan)]
    (solr/get-doc id c)
    (go
     (let [result (<! c)
           retrieved-doc (first (get-in result [:response :docs]))]
       (om/update! doc retrieved-doc)))))

(defn search
  ([q page page-size selected-facet-values app owner]
     "Search with the current query, paging etc. in solr, update the state with the results"
     (let [search-chan (om/get-shared owner :search-chan)]
       (solr/search q selected-facet-values page page-size search-chan)
       (go
        (let [result (<! search-chan)
              docs (get-in result [:response :docs])
              nof-docs (get-in result [:response :numFound])
              highlighting (get-in result [:highlighting])
              facet-fields (get-in result [:facet_counts :facet_fields])]
          (om/update! app :docs docs)
          (om/update! app :highlighting highlighting)
          (om/update! app :nof-docs nof-docs)
          (om/update! app :facet-fields facet-fields)
          (om/update! app :current nil)))))
  ([app owner]
     "Search with retrieving the parameters from the app state, runs async."
       (let [{:keys [q page page-size selected-facet-values]} @app]
         (search q page page-size selected-facet-values app owner))))


(defn search-from-box [app owner]
  (let [q (frontpage-client.util/input-value owner "query")]
       (om/update! app :q q)
       (om/update! app :page 0)
       (om/update! app :selected-facet-values {})
       (search app owner)))

(defn search-box [app owner]
  (reify
    om/IRender
    (render [this]
      (dom/div #js {:className "row"}
               (dom/div #js {:className "large-1 columns"}
                        ( dom/label #js {:className "right inline" :htmlFor "query"} "query:"))
               (dom/div #js {:className "large-4 columns"}
                        (dom/input #js {:type "text" :placeholder (if-let [q (:q app)] q "query")
                                        :name "query" :ref "query" :id "query"
                                        :onKeyPress (fn [e] (when (= "Enter" (.-key e))
                                                              (search-from-box app owner)))}))
               (dom/div #js {:className "large-1 columns"}
                        (dom/a #js {:className "tiny radius button inline left"
                                    :onClick (fn [] (search-from-box app owner))}
                               "Search"))
               (dom/div #js {:className "large-4 columns"})))))


(defn toggle-current [owner doc]
  "Called async."
  (if (om/get-state owner :current)
    (om/set-state! owner :current false)
    (do
      (om/set-state! owner :current true)
      (set-doc doc (:id @doc))))
  false)

(defn row-doc-changed [row-doc new-doc]
  (om/update! row-doc new-doc))

(defn result-item [doc owner {:keys [highlighting]}]
  (reify
    ;; When seeing a new document, revert to the initial non-current / non editing state
    om/IWillReceiveProps
    (will-receive-props [this next-doc]
      (when-let [prev-doc (om/get-props owner)]
        (when-not (= (:id prev-doc) (:id next-doc))
          (om/set-state! owner :current false))))
    om/IRender
    (render [this]
      (apply dom/div #js {:className "result-item"} 
             (concat
              [(dom/h4 nil
                       (dom/a #js {:href (str "?q=id:\"" (:id doc) "\"")
                                   :onClick (fn [_] (toggle-current owner doc))}
                              (:title doc)))]
              (if (om/get-state owner :current)
                [(om/build frontpage-client.document/current-doc doc
                           {:opts {:doc-changed-fn (partial row-doc-changed doc)}})]
                [(when highlighting 
                   (frontpage-client.util/html-dangerously dom/div {:className "summary"} (first (:text highlighting))))
                 (frontpage-client.document/metadata doc)]))))))

(defn result-list [app owner]
  (reify
    om/IRender
    (render [this]
      (let [pagination-opts {:opts {:page-changed-fn (fn [page] 
                                                       (search app owner))}}]
        (dom/div #js {:className "row"}
                 (dom/h2 nil (str (:nof-docs app) " " "Results"))
                 (om/build frontpage-client.pagination/pagination app pagination-opts)
                 (apply dom/div #js {:className "row"}
                        (for [doc (:docs app)]
                          (om/build result-item doc
                                    {:opts {:highlighting (get-in app [:highlighting (keyword (:id doc))])}
                                     :init-state {:current (= 1 (:nof-docs app))}})))
                 (om/build frontpage-client.pagination/pagination app pagination-opts))))))

;; Statistics component
;; Answer facts about listed components etc.
(defn statistics [app owner]
  (reify
    om/IRender
    (render [this]
      (dom/table nil
       (dom/thead nil
        (dom/tr nil
         (dom/td nil "mode")
         (dom/td nil "count"))
        (apply dom/tbody nil
               (for [mode [:listed :selected :edited]]
                 (dom/tr nil
                         (dom/td nil (name mode))
                         (dom/td nil (statistics/count (om/get-shared owner :db) mode))))))))))

(defn doc-ids [docs]
  "Answer a set of all the ids in docs"
  (set (map (fn [doc] (:id doc)) docs)))

(defn tx-listen [conn tx-data root-cursor]
  (let [path (:path tx-data)
        new-docs (get-in tx-data [:new-state :docs])
        old-docs (get-in tx-data [:old-state :docs])
        old-doc-ids (doc-ids old-docs)
        new-doc-ids (doc-ids new-docs)]
    (when-not (= old-doc-ids new-doc-ids)
      (doseq [doc new-docs]
        (statistics/add conn doc :listed)))
    ;; TODO: use clojure.core/match ?
    (when (= (first path) :docs)
      (let [new-doc (get new-docs (second path))
            old-doc (get old-docs (second path))]
        (statistics/add conn new-doc :selected)
        (when (and old-doc (not= old-doc new-doc))
          (statistics/add conn new-doc :edited))))))
      

;; select handler for the facet list.
(defn on-select [app owner]
  (om/update! app :page 0) ; reset the paging.
  (search app owner))

(defn root [state owner opts]
   (reify
     om/IRender
     (render [this]
       (dom/div nil
                (dom/div #js {:className "row"}
                         (dom/div #js {:className "large-2 columns"} 
                                  (om/build statistics state))
                         (dom/div #js {:className "large-8 columns"}
                                  (dom/h1 nil "FrontPage")))
                (dom/div #js {:className "row"}
                         (dom/div #js {:className "large-2 columns"} 
                                  (om/build frontpage-client.facets/facets-list state
                                            {:opts {:on-select-fn on-select}}))
                         (dom/div #js {:className "large-8 columns"}
                                  (om/build search-box state)
                                  (om/build result-list state)))
                (dom/div #js {:className "row"}
                         (dom/div #js {:className "large-2 columns"})
                         (dom/div #js {:className "large-8 columns"}))))
     om/IWillMount
     (will-mount [this]
       (let [q (:q opts)]
         (when-not (clojure.string/blank? q)
           (om/update! state :q q) ; not directly visible with (:q state) ?
           (let [{:keys [page page-size selected-facet-values]} state]
             (search q page page-size selected-facet-values state owner)))))))


(def app-state {:docs [] :highlighting {} :q nil :page 0 :page-size 10 :nof-docs 0
                :facet-fields [] :selected-facet-values {}})

(secretary/defroute "*" [query-params]
  (let [q (:q query-params)
        conn (frontpage-client.statistics/create-conn)]
    (om/root root app-state 
             {:opts {:q q}
              :target (. js/document (getElementById "app"))
              :shared {:search-chan (chan) :db conn}
              :tx-listen (partial tx-listen conn)})))

(secretary/dispatch! (.-URL js/document))

