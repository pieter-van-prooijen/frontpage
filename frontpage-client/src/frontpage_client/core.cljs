(ns frontpage-client.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [frontpage-client.solr :as solr]
            [frontpage-client.search]
            [cljs.core.async :refer [<! >! take! chan pub sub]]
            [frontpage-client.document]
            [frontpage-client.util :as util]
            [frontpage-client.pagination]
            [frontpage-client.facets]
            [frontpage-client.statistics :as statistics]
            [clojure.string]
            [goog.crypt.base64 :as b64]
            [secretary.core :as secretary :include-macros true]
            [domkm.silk :as silk])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(util/set-print!)

(defn set-doc [doc id]
  "Replace the specified document with a new doc (using its id to retrieve it from solr)."
  (let [c (chan)]
    (om/update! doc :body "Loading...")
    (solr/get-doc id c)
    (go
      (let [result (<! c)
            retrieved-doc (first (get-in result [:response :docs]))]
        (om/update! doc retrieved-doc)))))


(defn search-from-box [app owner]
  (let [q (util/input-value owner "query")]
    (om/update! app :q q)
    (om/update! app :facets {})
    (om/update! app :page 0)
    (frontpage-client.search/search app)))

(defn search-box [app owner]
  (om/component
   (html [:form.row
          [:div.large-1.columns
           [:label.right.inline {:for "query"} "query:"]]
          [:div.large-4.columns
           [:input {:type "text" 
                    :placeholder (if-let [q (:q app)] q "query")
                    :name "q" :ref "query" :id "query"
                    :on-key-down (fn [e] (when (= "Enter" (.-key e))
                                           (search-from-box app owner)
                                           (.preventDefault e)))}]]
          [:div.large-1.columns.end
           [:a.button.tiny.inline.left.radius {:on-click (fn [] (search-from-box app owner))} "Search"]]])))

(defn toggle-current [owner doc]
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
      (html [:div.result-item
             [:h4
              [:a {:href (str "?q=id:\"" (:id doc) "\"")
                   :on-click (fn [e]
                               (toggle-current owner doc)
                               (.preventDefault e))}
               (:title doc)]]
             (if (om/get-state owner :current)
                (om/build frontpage-client.document/current-doc doc
                          {:opts {:doc-changed-fn (partial row-doc-changed doc)}})
                (when highlighting 
                  (util/html-dangerously
                   dom/div {:className "summary"} (first (:text highlighting)))))
             (om/build frontpage-client.document/metadata doc)]))))

(declare routes) ; pass this as a parameter or global state ?

(defn result-list [app owner]
  (om/component 
   (let [from-app (select-keys app frontpage-client.pagination/pagination-keys)
         pagination-data (merge from-app {:page-changed-fn
                                          (fn [page] 
                                            (om/update! app :page page)
                                            (frontpage-client.search/search app))
                                          :routes @routes})]
     (html [:div.row
            [:div.large-12.columns
             [:h2 (str (:nof-docs app) " " "Results")]
             (om/build frontpage-client.pagination/pagination pagination-data)
             [:div.row
              [:div.large-12.columns
               (for [doc (:docs app)]
                 (let [highlighting (get-in app [:highlighting (keyword (:id doc))])]
                   (om/build result-item doc
                             {:opts  {:highlighting highlighting}
                              :react-key (:id doc) ; prevent warnings from react.
                              :init-state {:current (= 1 (:nof-docs app))}})))]]
             (om/build frontpage-client.pagination/pagination pagination-data)]]))))

(defn root [{:keys [q rendered-from-nashorn] :as app} owner {render-state :render-state}]
  "Setup the root react component"
  (reify
    om/IWillMount
    ; Initial render, use the query found in the current global state
    (will-mount [_]
      (when-not (or rendered-from-nashorn (clojure.string/blank? q))
        (frontpage-client.search/search app)))
    om/IRender
    (render [_]
      (html [:div
             [:div.row
              [:div.large-3.columns.hide]
              [:div.large-9.columns
               [:h1 (str "Frontpage")]]]
             [:div.row
              [:div.large-3.columns (om/build frontpage-client.facets/facets-list app)]
              [:div.large-9.columns 
               (om/build search-box app)
               (om/build result-list app)]
              ;; Render the current application state as edn in base64
              (when render-state
                (util/html-dangerously dom/script 
                                       {:type "text/javascript"}
                                       (str "initial_app_state = \"" (b64/encodeString (pr-str app)) "\"")))]]))))

(defn mount-root [state]
  (let [pub-chan (chan)]
    (om/root root state 
             {:target (. js/document (getElementById "app"))
              :shared {:publication-chan pub-chan
                       :publication (pub pub-chan :topic)}})))
 
(defn render-root-to-str [state]
  (let [pub-chan (chan)]
    (dom/render-to-str (om/build root (om/root-cursor state) {:opts {:render-state true}}))))

;; Keep the global state when this file is reloaded by figwheel.
;; Optionally read from the initial-app-state EDN base 64 encoded variable.
(defonce app-state (atom 
                    (if (exists? js/initial-app-state)
                      (let [app-str (b64/decodeString js/initial-app-state)]
                        ;; FIXME: keywords which contain a "//" are replaced with a single "/"
                        ;; (interpreted as a namespaced keyword ?
                        (cljs.reader/read-string app-str))
                      {:docs [] :highlighting {} :q "" :page 0 :page-size 10 :nof-docs 0
                       :facets {}})))

;; All app state keys settable via the url and their silk matchers, wrappers for silk/int and bool.
(defn- silk-string [k default]
  (silk/? k default))

(defn- silk-int [k default]
  (silk/? (silk/int k) default))

(defn- silk-bool [k default]
  (silk/? (silk/bool k) default))

(def query-params->matcher {:q silk-string :page silk-int :page-size silk-int})

(defn in-browser? []
  (exists? js/document))
 
;; create routes from the app state
(defn create-routes []
  (silk/routes [[:query [(if (in-browser?) [] ["nashorn"]) ; 
                         ;; query strings, convert from the specification map above.
                         (->> (for [[k matcher] query-params->matcher]
                                [(name k) (matcher k {k (k @app-state)})])
                              (apply concat)
                              (apply hash-map))]]]))

(defonce routes (atom (create-routes)))

;; The routes change if the app state changes.
(defn set-state-from-routes [url]
  (when-let [matched (silk/arrive @routes url)]
    (doseq [k (keys query-params->matcher)]
      (om/update! app-state k (k matched)))
    (reset! routes (create-routes))))

;; Distinguish between client / server rendering.

;; Rendering in the browser, invoked automatically when loading this file.
(when (in-browser?)
  (set-state-from-routes (.-URL js/document))
  (mount-root app-state))

;; Server rendering, call this function from outside the Nashorn engine.
(defn render-from-nashorn [url]
  ;; Simulate some om/root functionality, om/setup is really a private function, cursors won't work otherwise.
  (om/setup app-state (gensym) nil)
  (set-state-from-routes url)
  (om/update! app-state :rendered-from-nashorn true)
  ;; Simulate IWillMount
  (when-not (clojure.string/blank? (:q @app-state))
        (frontpage-client.search/search app-state))
  (render-root-to-str app-state))
 


