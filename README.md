# Frontpage

Experiment with a single page webapp which opens up a large document set
using Clojurescript, Clojure and Solr, in combination with
React/Om and other Clojurescript libraries. Currently, it does full-text
search, facets, url routing and inline editing.

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
To compile the webpage app and making changes to the code, go to the _frontpage-client_ directory and start:
```
$ lein deps
$ lein cljsbuild clean
$ lein cljsbuild once
```

Or use the "lein figwheel" command which reloads code changes automatically in the browser.

# Running

In the _frontpage-client_ subproject, start the jetty server and solr reverse proxy using:
```
$ lein servlet run
```
This will open the webapp at http://localhost:3000 in a new browser window
and makes the solr server available at http://localhost:3000/solr, allowing
the javascript to query and post to solr directly (obeying the same-origin restrictions present in XHR requests).

Features available in the application:

* Type a query in lucene syntax for a full-text search
* Use the _q_ request parameter to specify a query via the url.
* Narrow / broaden search results by toggling the facets on the left side.
* The date facets are hierarchical.
* Select a document by clicking on its title, you can then edit the
  document in-place.
* Show all documents on a certain date by clicking the article timestamp.

The app is styled using the [Zurb Foundation](foundation.zurb.com) CSS framework.

# Code
Global state:
- current query
- current list of search results
- current facets received from solr, containing a list of facet-values and the current state (value is selected or
  not)
- current selected document


Om control flow:
1. input event handler invokes action (e.g. change the current "q" and search with q)
2. search invokes a call to solr which puts the result on a supplied channel when received from the server
3. search reads the result from the channel and updates the global state.

*problem:* When selecting a facet it's already marked as selected in the app state (and possibly rendered), but the
corresponding child facets and values are not yet received from solr due to the async retrieval.
Causes inconsistent app state, react errors etc.

*solution:* "staged async execution", app state is "copied", copy is mutated and used in searched and thrown away
afterwards. After receiving the results, the mutation is repeated, together with the results.

See frontpage-client.utils/staged-async-exec for details. 

i18next for facet titles and values translation

# Development
If you want a browser REPL, startup a nrepl (for instance in Emacs with "M-x
cider-jack-in') and execute
`(require 'init-repl)`. After that, reload the main page at http://localhost:3000/index.html and your can type
expressions in the repl session which are evaluated by the brower.

Use the `lein cljsbuild auto` command to automatically compile modified
Clojurescript files.

Or use the `lein figwheel` command to automatically compile and reload changed sources in to the application. This
automatically uses the clsjbuild functionality.

# Todo

Use solr cell to extract the html content from the body of a post (see
https://cwiki.apache.org/confluence/display/solr/Uploading+Data+with+Solr+Cell+using+Apache+Tika).
The import uses jsoup to extract the text, but this is not available when
editing documents using Clojurescript.


