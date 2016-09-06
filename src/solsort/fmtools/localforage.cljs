(ns solsort.fmtools.localforage
  (:require
   [solsort.util :refer [<p log]]
   [cljsjs.localforage]))

(def localforage-db
  (.createInstance js/localforage
                   #js {:name "JsonData"
                        :storeName "JsonData"
                        :description "JSON data store"}))

(defn <localforage-db [k] (<p (.getItem localforage-db k)))
(defn <localforage-db! [k v] (<p (.setItem localforage-db k v)))
