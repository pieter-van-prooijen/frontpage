#! /bin/bash

# Run this script in the top of frontpage project.
SOLR_HOME=$PWD/solr

# Change this to your solr installation, must contain the "example" directory.
SOLR_INSTALL=/home/pieter/packages/solr-7.2.1

# Starts a jetty server at port 8983
cd $SOLR_INSTALL/example
$SOLR_INSTALL/bin/solr start -f -a -Dsolr.solr.home=$SOLR_HOME $*
