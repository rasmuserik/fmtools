#!/bin/bash -vx

# copy id and version from project.clj into config.xml
export APP_ID=`grep defproject project.clj | head -n 1 | sed -e 's/ *".*//' | sed -e 's/.* //' | sed -e 's/\\//./'`
export VERSION=`grep defproject project.clj | head -n 1 | sed -e 's/.* "//' | sed -e 's/".*//'`
perl -pi -e "s/widget id=\"[^\"]*\" version=\"[^\"]*/widget id=\"$APP_ID\" version=\"$VERSION/" config.xml 

# cleanup before building
lein clean 
lein cljsbuild once dist 

# Manifest file
#
echo "CACHE MANIFEST" > index.appcache
echo "# `date`" >> index.appcache
find assets -type f >> index.appcache
echo "index.js" >> index.appcache

cat doc/intro.md > README.md
for SRC in main
do
cat src/solsort/*/${SRC}.cljs | 
  sed -e "s/^[^/]/    \0/" | sed -e s'/^ *[;][;] \?//' >> README.md
done
