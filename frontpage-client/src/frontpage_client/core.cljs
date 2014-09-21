(ns frontpage-client.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontpage-client.solr :as solr]
            [frontpage-client.search]
            [cljs.core.async :refer [<! >! take! chan]]
            [frontpage-client.document]
            [frontpage-client.util]
            [frontpage-client.pagination]
            [frontpage-client.facets]
            [frontpage-client.statistics :as statistics]
            [clojure.string]
            [datascript :as d]
            [secretary.core :as secretary :include-macros true])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn set-doc [doc id]
  "Replace the specified document with a new doc (using its id to retrieve it from solr)."
  (let [c (chan)]
    (solr/get-doc id c)
    (go
     (let [result (<! c)
           retrieved-doc (first (get-in result [:response :docs]))]
       (om/update! doc retrieved-doc)))))


(defn search-from-box [app owner]
  (let [q (frontpage-client.util/input-value owner "query")]
       (om/update! app :q q)
       (om/update! app :facets {})
       (om/update! app :page 0)
       (frontpage-client.search/search app)))

(defn search-box [app owner]
  (om/component
   (dom/form #js {:className "row"}
             (dom/div #js {:className "large-1 columns"}
                      ( dom/label #js {:className "right inline" :htmlFor "query"} "query:"))
             (dom/div #js {:className "large-4 columns"}
                      (dom/input #js {:type "text" :placeholder (if-let [q (:q app)] q "query")
                                      :name "q" :ref "query" :id "query"
                                      :onKeyDown (fn [e] (when (= "Enter" (.-key e))
                                                           (.preventDefault e)
                                                           (search-from-box app owner)))}))
             (dom/div #js {:className "large-1 columns end"}
                      (dom/a #js {:className "tiny radius button inline left"
                                  :onClick (fn [] (search-from-box app owner))}
                             "Search")))))

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
    (will-receive-props [_ next-doc]
      (when-let [prev-doc (om/get-props owner)]
        (when-not (= (:id prev-doc) (:id next-doc))
          (om/set-state! owner :current false))))
    om/IRender
    (render [_]
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
                 (om/build frontpage-client.document/metadata doc)]))))))

(defn result-list [app owner]
  (om/component
   (let [pagination-opts {:opts {:page-changed-fn (fn [page] 
                                                    (frontpage-client.search/search app))}}]
     (dom/div #js {:className "row"}
              (dom/div #js {:className "large-12 columns"}
                       (dom/h2 nil (str (:nof-docs app) " " "Results"))
                       (om/build frontpage-client.pagination/pagination app pagination-opts)
                       (dom/div #js {:className "row"}
                                (apply dom/div #js {:className "large-12 columns"}
                                       (for [doc (:docs app)]
                                         (let [highlighting (get-in app [:highlighting (keyword (:id doc))])]
                                           (om/build result-item doc
                                                     {:opts  {:highlighting highlighting}
                                                      :init-state {:current (= 1 (:nof-docs app))}})))))
                       (om/build frontpage-client.pagination/pagination app pagination-opts))))))

(defn root [app owner {:keys [q]}]
  "Setup the root react component"
  (reify
    om/IWillMount
    ; Initial render, use the query found in the page load request.
    (will-mount [_]
      (when-not (clojure.string/blank? q)
        (frontpage-client.search/search app (fn [cursor] (om/update! cursor :q q))))
      (frontpage-client.facets/install-facet-select-loop app owner))
    om/IRender
    (render [_]
      (dom/div nil
               (dom/div #js {:className "row"}
                        (dom/div #js {:className "large-3 columns hide"} ; not shown
                                 (om/build statistics/statistics app))
                        (dom/div #js {:className "large-9 columns"}
                                 (dom/h1 nil "Frontpage")))
               (dom/div #js {:className "row"}
                        (dom/div #js {:className "large-3 columns"} 
                                 (om/build frontpage-client.facets/facets-list (:facets app)))
                        (dom/div #js {:className "large-9 columns"}
                                 (om/build search-box app)
                                 (om/build result-list app)))
               (dom/div #js {:className "row"}
                        (dom/div #js {:className "large-3 columns"})
                        (dom/div #js {:className "large-9 columns"}))))))

 
;; Keep the global state when this file is reloaded by figwheel.
(def initial-app-state {:docs [] :highlighting {} :q nil :page 0 :page-size 10 :nof-docs 0
                :facets {}})

;; Define a route which runs a search based on the "q" request parameter.
;; Creates the global om component and shared state when invoked.
(secretary/defroute "*" [query-params]
  (let [q (:q query-params)
        conn (frontpage-client.statistics/create-conn)]
    (om/root root initial-app-state 
             {:opts {:q q}
              :target (. js/document (getElementById "app"))
              :shared {:facet-select-chan (chan)
                       :db conn}
              :tx-listen (partial statistics/tx-listen conn)})))

(secretary/dispatch! (.-URL js/document))
