(ns solsort.fmtools.localforage
  (:require
   [solsort.util :refer [<p log]]
   [cljsjs.localforage]))

(def localforage-db
  (.createInstance js/localforage
                   #js {:name "JsonData"
                        :storeName "JsonData"
                        :description "JSON data store"}))

(def localforage-images
  (.createInstance js/localforage
                   #js {:name "ImageData"
                        :storeName "ImageData"
                        :description "Image store"}))

;; TODO We should be nice and use the async interface of localforage

(defn <localforage-db [k] (<p (.getItem localforage-db k)))
(defn <localforage-db! [k v] (<p (.setItem localforage-db k v)))

(defn <localforage-images [k] (<p (.getItem localforage-images k)))
(defn <localforage-images! [k v] (<p (.setItem localforage-images k v)))
