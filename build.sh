#!/bin/bash -vx

# copy id and version from project.clj into config.xml
export APP_ID=`grep defproject project.clj | head -n 1 | sed -e 's/ *".*//' | sed -e 's/.* //' | sed -e 's/\\//./'`
export VERSION=`grep defproject project.clj | head -n 1 | sed -e 's/.* "//' | sed -e 's/".*//'`
perl -pi -e "s/widget id=\"[^\"]*\" version=\"[^\"]*/widget id=\"$APP_ID\" version=\"$VERSION/" config.xml 

# cleanup before building
lein clean 
lein cljsbuild once dist 
lein clean

# Manifest file
#
echo "CACHE MANIFEST" > index.appcache
echo "# `date`" >> index.appcache
find -L assets -type f >> index.appcache
echo "index.js" >> index.appcache
# echo "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.1.8/semantic.min.css" >> index.appcache
echo "https://fonts.googleapis.com/css?family=Open+Sans:300,300italic,400,400italic,600,600italic,700,700italic|Open+Sans+Condensed:300,300italic,700" >> index.appcache
echo "NETWORK:" >> index.appcache
echo "*" >> index.appcache

#echo "" > README.md
#for SRC in main
#do
#cat src/solsort/*/${SRC}.cljs | 
#  sed -e "s/^[^/]/    \0/" | sed -e s'/^ *[;][;] \?//' >> README.md
#done
