(ns solr-import.core
  (:require [clojure.data.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zip-xml]
            [clojure.java.io :as io]
            [clojure.string]
            [flux.http]
            [flux.core :as flux]
            [clj-time.core]
            [clj-time.format])
  (:import [org.jsoup Jsoup]))

(def post-formatter (clj-time.format/formatter "yyyy-MM-dd' 'HH:mm:ss"))
(def solr-formatter (clj-time.format/formatter "yyyy-MM-dd'T'HH:mm:ss'Z'"))
(def post-tz (clj-time.core/time-zone-for-offset -7))

(defn convert-date [s]
  "Convert date from the xml format to solr, assumes posts are in pacific daylight savings time"
  (let [date (clj-time.format/parse post-formatter s)
        local (clj-time.core/from-time-zone date post-tz)]
    (clj-time.format/unparse solr-formatter local)))

 ;; TODO move this to solr text extraction using Tikka, so it also works when editing new documents
(defn extract-html-text [s]
  "Extract text from the html formatted string s"
  (.text (Jsoup/parse s )))

(defn extract-post 
  "Answer a post map for a specified row in the xml"
  [row-loc]
  (let [post (into {} (for [key [:permalink :title :body :author :categories :created_on :body_more :categories]]
                        [key (zip-xml/text (zip-xml/xml1-> row-loc (zip-xml/tag= key)))]))
        ;; replace empty values designated by NULL, trim the value.
        filtered (into {} (map (fn [[key val]] [key (if (= val "NULL") "" (clojure.string/trim val))]) post))
        categories (remove clojure.string/blank? (clojure.string/split (:categories filtered) #",+"))
        body (str (:body post) (:body_more filtered))]
        (-> filtered
            (assoc :created_on (convert-date (:created_on filtered)))
            (assoc :body body)
            (assoc :extracted_body_text (extract-html-text body))
            (assoc :categories (if (seq categories) categories ["uncategorized"]))
            (assoc :id (:permalink filtered)))))

(defn extract-posts [input-stream]
  "Answer a lazy seq of posts reading from input-stream."
  (let [xml (xml/parse input-stream)
        custom-loc (clojure.zip/xml-zip xml)]
    (for [row-loc (zip-xml/xml-> custom-loc :row)]
      (extract-post row-loc))))

(defn create-connection [] (flux.http/create "http://localhost:8983/solr" :frontpage))

(defn load-posts [file]
  (with-open [input-stream (io/input-stream file)]
    (flux/with-connection (create-connection)
      (doseq [post-partition (partition 100 100 [] (extract-posts input-stream))]
        (doseq [post post-partition]
          (flux/add post))
        (flux/commit)))))

(defn delete-all []
  (flux/with-connection (create-connection)
    (flux/delete-by-query "*:*")
    (flux/commit)))

(defn -main [& args]
  "Run with the first argument being the file to import, or --delete to delete all"
  (let [first-arg (first args)]
    (if (= first-arg "--delete")
      (delete-all)
      (load-posts first-arg))))

(defn collapse-same
  ([coll]
     "Answer a lazy seq with ranges of same value items in coll collapsed into one.
      (collapse-same [1 1 2 3 3 1 1]) => (1 2 3 1).
      Allows nil value items in the seq."
     (if (seq coll)
       (collapse-same coll (first coll))
       (empty coll)))
  ([coll v]
     (lazy-seq
      (if (and (seq coll) (= (first coll) v))
        (collapse-same (rest coll) v)
        (cons v (collapse-same coll))))))


