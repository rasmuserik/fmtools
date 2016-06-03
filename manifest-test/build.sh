#!/bin/bash -vx

# Manifest file
#
echo "CACHE MANIFEST" > index.appcache
echo "# `date`" >> index.appcache
find assets -type f >> index.appcache
echo "index.js" >> index.appcache
