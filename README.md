# Frontpage

Experiment with a single page webapp which opens up a large document set
using Clojurescript, Clojure and Solr, in combination with
React/Om and other Clojurescript libraries. Currently, it does full-text
search, facets, url routing, inline editing and statistics (with the
Datascript library).

It uses the BoingBoing blog post dump as its document set.

The name Frontpage is inspired by [this blog post](http://www.pitheringabout.com/?p=1018) by John Pither which describes
using Clojure + ElasticSearch to power the Daily Mail newspaper website.

The project contains the following modules:
* frontpage-client, the Clojurescript single page webapp.
* solr-import, the Clojure BoingBoing post importer for Solr.
* solr, the Solr configuration.


# Requirements

Requirements: Oracle / OpenJdk Jdk 1.7, Leiningen 2, Solr 4.7+ and
the Tidy XML cleaner.

# Installation

## Data

Fetch the blogposts from https://s3.amazonaws.com/bbpostdump/bbpostdump.xml.zip

Use tidy to clean up unknown character encodings:
```
$ tidy -xml -latin1 -indent < bbpostdump.xml > bbpostdump-tidy.xml
```

## Solr
Install [solr 4.7 or higher](http://lucene.apache.org/solr) and adjust the
_run-solr.sh_ script in the toplevel directory of the project to your installation directory.

Start solr with the run_solr.sh script, it will start a Jetty server at port 8983.
To start with a clean collection (for example, when using a new schema), remove all files under the
solr/core0/data directory

## Importing Data
Goto the _solr-import_ sub project and run (optionally removing all
existing documents first):
```
$ lein run --delete
$ lein run <path-to-bbpostdump-tidy.xml
```

This can take a while (10 minutes with an SSD), see the output of the _run-solr.sh` script to check
if documents are added correctly.

Inspect the resulting collection/core (named "frontpage") using the Solr admin interface
at [http://localhost:8983/solr]

# Compiling Clojurescript
To compile the webpage app and making changes to the code, go to the frontpage-client directory and start:
```
$ lein deps
$ lein cljsbuild clean
$ lein cljsbuild once
```

# Running

Start the jetty server and solr reverse proxy using:
```
$ lein servlet run
```
This will open the webapp at http://localhost:3000 in a new browser window
and makes the solr server available at http://localhost:3000/solr, allowing
the javascript to query and post to solr directly.

Features available in the application:

* Type a query in lucene syntax for a full-text search
* Use the _q_ request parameter to specify a query via the url.
* Narrow / broaden search results by the facets on the left side, these are toggles.
* Select a document by clicking on its title, you can then edit the
  document in-place.
* The statistics table in the top right lists the number of listed
  documents, this is reset after a reload.

The app is styled using the [Zurb Foundation](foundation.zurb.com) CSS framework.

# Development
If you want a browser REPL, startup a nrepl (for instance in Emacs with "M-x
cider-jack-in') and execute
`(require 'init-repl)`. After that, open the  URL
http://localhost:9000/index.hml in the browser. (The Cljs repl doesn't work with the
http://localhost:3000 location because of the urls it generates)

Use the `lein cljsbuild auto` command to automatically compile modified
Clojurescript files.

# Todo
Use solr cell to extract the html content from the body of a post (see
https://cwiki.apache.org/confluence/display/solr/Uploading+Data+with+Solr+Cell+using+Apache+Tika).
The import ses jsoup to extract the text, but this is not usable when
editing documents.

