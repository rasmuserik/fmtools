(ns solsort.fmtools.util
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]]
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
   [solsort.util :as util
    :refer
    [<p <ajax <seq<! js-seq normalize-css load-style! put!close! chan?
     parse-json-or-nil log page-ready render dom->clj next-tick]]
   [devtools.core :as devtools]
   [cljs.core.async :as async :refer [>! <! chan put! take! timeout close! pipe tap mult]]
   [cognitect.transit :as transit]))


(def third util/third)
(def delay-fn util/delay-fn)
(def <chan-seq util/<chan-seq)
(def to-map util/to-map)
(def timestamp->isostring util/timestamp->isostring)
(def str->timestamp util/str->timestamp)
(def throttle util/throttle)
(def tap-chan util/tap-chan)

(defn clj->json [s] (transit/write (transit/writer :json) s))
(defn json->clj [s] (transit/read (transit/reader :json) s))

(when js/window.applicationCache
  (aset js/window.applicationCache "onupdateready" #(js/location.reload)))

(defonce dev-tools (devtools/install!))
(defonce empty-choice "· · ·")
(defn fourth-first [[v _ _ k]] [k v])
(defn <localforage [k] (<p (.getItem js/localforage k)))
(defn <localforage! [k v] (<p (.setItem js/localforage k v)))
(defn delta
  "get changes from a to b"
  [from to]
  (if (= from to)
    (if (coll? to) {} to)
    (if (coll? to)
      (let [from (to-map from)
            to (to-map to)
            ks (distinct (concat (keys from) (keys to)))
            ks (filter #(not= (from %) (to %)) ks)]
        (into {} (map (fn [k]  [k (delta (from k) (to k))])  ks)))
      to)))


