(ns solr-import.core
  (:require [clojure.data.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zip-xml]
            [clojure.java.io :as io]
            [clojure.string]
            [clj-time.core :as t]
            [clj-time.format])
  (:import [org.apache.solr.client.solrj.impl HttpSolrClient$Builder]
           [org.apache.solr.common SolrInputDocument]
           [org.jsoup Jsoup]))

(def post-formatter (clj-time.format/formatter "yyyy-MM-dd' 'HH:mm:ss"))
(def solr-formatter (clj-time.format/formatter "yyyy-MM-dd'T'HH:mm:ss'Z'"))
(def post-tz (clj-time.core/time-zone-for-offset -7)) ; pacific daylight savings time.

(defn get-date [s]
  "Answer a date from the xml format, in the UTC timezone"
  (let [date (clj-time.format/parse post-formatter s)]
    (clj-time.core/from-time-zone date post-tz)))

(defn to-solr [d]
  "Convert a dat in xml format to solr utc format."
  (clj-time.format/unparse solr-formatter d))

 ;; TODO move this to solr text extraction using Tikka, so it also works when editing new documents
(defn extract-html-text [s]
  "Extract text from the html formatted string s"
  (.text (Jsoup/parse s )))

;; Id's are used as keywords, so whitelist allowed characters
(defn create-id [s]
  (clojure.string/replace s #"[^A-ZAa-z0-9\-_]" "-"))

(defn extract-post 
  "Answer a post map for a specified row in the xml"
  [row-loc]
  (let [post (into {} (for [key [:permalink :title :body :author :categories :created_on :body_more :categories]]
                        [key (zip-xml/text (zip-xml/xml1-> row-loc (zip-xml/tag= key)))]))
        ;; replace empty values designated by NULL, trim the value.
        filtered (into {} (map (fn [[key val]] [key (if (= val "NULL") "" (clojure.string/trim val))]) post))
        categories (remove clojure.string/blank? (clojure.string/split (:categories filtered) #",+"))
        body (str (:body post) (:body_more filtered))
        created-on (get-date (:created_on filtered))]
    (-> filtered
        (assoc :created_on (to-solr created-on))
        (assoc :created_on_year (t/year created-on))
        (assoc :created_on_month (t/month created-on))
        (assoc :created_on_day (t/day created-on))
        (assoc :body body)
        (assoc :extracted_body_text (extract-html-text body))
        (assoc :categories (if (seq categories) categories ["uncategorized"]))
        (assoc :id (create-id (:permalink filtered))))))

(defn extract-posts [input-stream]
  "Answer a lazy seq of posts reading from input-stream."
  (let [xml (xml/parse input-stream)
        custom-loc (clojure.zip/xml-zip xml)]
    (for [row-loc (zip-xml/xml-> custom-loc :row)]
      (extract-post row-loc))))

(defn create-client [] (.build (HttpSolrClient$Builder. "http://localhost:8983/solr/frontpage")))

(defn ^SolrInputDocument to-solr-input-document [m]
  (let [d (SolrInputDocument. (into-array String []))]
    (doseq [[k v] m]
      (.addField d (name k) v))
    d))

(defn load-posts [file]
  (with-open [input-stream (io/input-stream file)]
    (let [client (create-client)]
      (->> (extract-posts input-stream)
           (map to-solr-input-document)
           (partition-all 500)
           (map (fn [part]
                  (.add client (.iterator part))
                  (.commit client)))
           (doall)))))

(defn delete-all []
  (let [client (create-client)]
    (.deleteByQuery client "*:*")))

(defn -main [& args]
  "Run with the first argument being the file to import, or --delete to delete all"
  (let [first-arg (first args)]
    (if (= first-arg "--delete")
      (delete-all)
      (load-posts first-arg))))


