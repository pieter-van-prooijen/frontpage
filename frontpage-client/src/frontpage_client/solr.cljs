(ns frontpage-client.solr
  (:require [goog.json :as json]
            [goog.net.XhrIo :as xhrio]
            [goog.Uri]
            [cljs.core.async :refer [<! >! chan put!]])
  (:require-macros [cljs.core.async.macros :refer [go]])) 

(enable-console-print!)

;; Retrieve/store frontpage document using the solr json api.

;; "json.wrf" as callback parameter name enables the solr jsonp response.
(def solr-collection-url "http://localhost:3000/solr/frontpage")
(def solr-select-url (str solr-collection-url "/select"))
(def solr-update-url (str solr-collection-url "/update"))

(defn- create-uri [base params]
 "Create a goog.Uri instance with the specified url, path and parameter map"
 (let [uri (goog.Uri. base)]
   (doseq [[k v] params]
     (.setParameterValues uri (name k) (clj->js v)))
   uri))

;; Main result callback from a /select query.
(defn- select-cb [c e]
  (let [result-js (.. e -target getResponseJson)
        result (js->clj result-js :keywordize-keys true)]
    (go (>! c result))))

;; m is a map of keyword-naming-field => value or values
(defn create-field-queries [m]
  (->> m
      (map (fn [[field values]]
                     (for [value (if (coll? values) (seq values) [values])]
                       (str (name field) ":\"" value "\""))))
      (apply concat)))

;; Frontpage document fields, with a subset to answer in a search query.
(def all-doc-fields [:id :title :created_on :author :categories :body])
(def search-doc-fields (remove #(= % :body) all-doc-fields))

(defn search [q fq page page-size c]
  "Search for q and put the result as a nested map on the supplied channel.
   fq is a map of field -> value queries (which are and-ed) to search for."
  (let [cb (partial select-cb c)
        params {:q q
                :fq (clj->js (create-field-queries fq))
                :wt "json"
                :fl (clj->js (map #(name %) search-doc-fields)) 
                :hl true :hl.fl "text"
                :start (* page page-size) :rows page-size
                :facet true :facet.field #js ["author" "categories"] :facet.mincount 1}]
    (xhrio/send (create-uri solr-select-url params) cb)))

(defn get-doc [id c]
  "Get the specified document"
  (let [cb (partial select-cb c)
        params {:q (str "id:\"" id "\"") :wt "json" :fl (clj->js (map #(name %) all-doc-fields))}]
    (xhrio/send (create-uri solr-select-url params) cb)))

(defn- put-cb [c e]
  "Put the event status of a put request on the channel."
  (let [status (.. e -target getStatus)]
    (println "Put response: " status)
    (go (>! c status))))

(defn put-doc [doc c]
  "Put the specified document in the solr collection and answer the result on the channel."
  (let [cb (partial put-cb c)
        params {:commit true :wt "json"}
        stripped-doc (select-keys doc all-doc-fields)
         ;; TODO move this to solr text extraction
        added-text-doc (assoc stripped-doc :extracted_body_text (:body doc))
        command {:add {:doc added-text-doc}} ; build the update command.
        content (json/serialize (clj->js command))
        headers #js {"Content-Type" "application/json"}]
    (xhrio/send (create-uri solr-update-url params) cb "POST" content headers)))


