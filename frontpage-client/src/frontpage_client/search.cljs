(ns frontpage-client.search
  (:require [frontpage-client.solr :as solr]
            [om.core :as om :include-macros true]
            [frontpage-client.util]))

;; Determine the current facet-fields to use in the query, using the parent /child hierarchy.
(defn facet-fields [facets]
  (->> (for [{:keys [child-key selected-values] :as facet} (vals facets)]
         (when-not (empty? selected-values)
           child-key))
       (remove nil?)
       (concat solr/facet-field-doc-fields)))

(defn- update-facet [facet-key counts gap facet]
  "Update the specified facet map with the new fields."
  (assoc facet :name (name facet-key) :counts counts :gap gap
         :child-key (facet-key solr/facet-field-parent-child)))

(defn update-facets-from-result [app search-result]
  "Put the facet related aspect of search-result in the global app state"
  (let [facet-fields (get-in search-result [:facet_counts :facet_fields])
        facet-ranges (get-in search-result [:facet_counts :facet_ranges])]
    (doseq [[facet-key counts] facet-fields]
      (om/transact! app [:facets facet-key] (partial update-facet facet-key counts nil)))
    (doseq [[facet-key {:keys [counts gap]}] facet-ranges] 
      (om/transact! app [:facets facet-key] (partial update-facet facet-key counts gap)))))

(defn process-search-result [result app]
  (let [docs (get-in result [:response :docs])
        nof-docs (get-in result [:response :numFound])
        highlighting (get-in result [:highlighting])]
    (om/update! app :docs docs)
    (om/update! app :highlighting highlighting)
    (om/update! app :nof-docs nof-docs)
    (update-facets-from-result app result)
    (om/update! app :current nil)))

(defn search-start
  ([q page page-size facets search-chan]
     "Search with the current query, paging etc. in solr, update the state with the results"
     (let [facet-fields (facet-fields facets)]
       (solr/search q facets page page-size facet-fields search-chan)))
  ([app search-chan]
     "Search with retrieving the parameters from the app state, runs async."
     (let [{:keys [q page page-size facets]} @app] ; explicitly deref to get the latest version
         (search-start q page page-size facets search-chan))))

(defn search
  ([app stage-fn]
     (frontpage-client.util/staged-async-exec search-start process-search-result app stage-fn))
  ([app]
     (search app (fn [_]))))
