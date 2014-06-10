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

Requirements: Oracle / OpenJdk Jdk 1.7, Leiningen 2, Solr 4.7+, Apache2 and
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

## Importing Data
Goto the _solr-import_ sub project and run (optionally removing all
existing documents first):
```
$ lein run --delete
$ lein run <path-to-bbpostdump-tidy.xml
```

This can take a while (10 minutes with an SSD), see the output of the _run-solr.sh` script to check
if documents are added correctly.

Check if the resulting frontpage collection is present in the Solr admin interface at
http://localhost:8983/solr/#/frontpage

## Apache
Install Apache and change the frontpage vhost defined in the
_apache-vhost-example.conf_ to reflect the installation directory of the
frontpage project (in the DocumentRoot directive).

This vhost also contains a proxy to the Solr server,
to allow POST's to Solr without running into the "Same Origin" policy of
the browser for Javascript requests.

In Debian / Ubuntu, this would require the following steps (as root):
```
 # cp apache-vhost-example.conf /etc/apache2/sites-available/frontpage.conf
 # a2ensite frontpage
 # service apache2 reload
```

Add the following line to your /etc/hosts file
```
127.0.0.1 frontpage.localdomain
```

# Compiling Clojurescript
To compile the webpage app and making changes to the code, go to the frontpage-client directory and start:
```
lein deps
lein cljsbuild clean
lein cljsbuild once
```

# Running

Open the webapp at: http://frontpage.localdomain and explore:

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
frontpage.localdomain url because of the urls it generates)

Use the `lein cljsbuild auto` command to automatically compile modified
Clojurescript files.


