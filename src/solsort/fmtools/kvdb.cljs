(ns solsort.fmtools.kvdb
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require
   [solsort.util :refer [<p log]]
   [solsort.fmtools.localforage :as lf]
   [cljs.core.async :as async :refer [>! <! chan put! take! timeout close! pipe]]))
(defonce cache (atom {}))

(defn <put [k v]
  (lf/<localforage-db! k v)
  )
(defn <get [k]
  (lf/<localforage-db k)
  )
(defn clear []
  (<p (.clear lf/localforage-db)))

(defn <all []
  (let [o (atom {})
        c (chan)]
    (.iterate lf/localforage-db
              (fn [v k]
                (swap! o assoc k v)
                js/undefined)
              (fn [] (put! c @o) (close! c)))
    c))
