(defproject solr-import "0.1.0-SNAPSHOT"
  ;;
  ;; Use "lein run <file>" to import a xml dump into solr, "lein run --delete" to delete all docs.
  :description "Import a BoingBoing XML dump into Solr"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [com.codesignals/flux "0.6.0"]
                 [clj-time "0.11.0"]
                 [org.slf4j/slf4j-api "1.7.18"]
                 [org.slf4j/slf4j-log4j12 "1.7.18"]
                 [commons-logging/commons-logging "1.2"]
                 [org.jsoup/jsoup "1.8.3"]]
  ;;:profiles {:dev {:jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8002"]}}
  :main solr-import.core)
