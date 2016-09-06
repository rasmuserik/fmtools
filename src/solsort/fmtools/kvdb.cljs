(ns solsort.fmtools.kvdb
  (:require
   [solsort.util :refer [<p log]]
   [solsort.fmtools.localforage :as lf]
   ))
(defonce cache (atom {}))

(defn <put [k v]
  (lf/<localforage-db! k v)
  )
(defn <get [k]
  (lf/<localforage-db k)
  )
(defn clear []
  (<p (.clear lf/localforage-db))
  )
