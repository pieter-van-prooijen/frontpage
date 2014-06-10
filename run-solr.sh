#! /bin/bash

# Run this script in the top of frontpage project.
SOLR_HOME=$PWD/solr

# Change this to your solr installation, must contain the "example" directory.
SOLR_INSTALL=/home/pieter/packages/solr-4.7.0

# Starts a jetty server at port 8983
cd $SOLR_INSTALL/example
java -jar start.jar -Dsolr.solr.home=$SOLR_HOME $*
