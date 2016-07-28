(ns solsort.fmtools.util
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]]
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
   [solsort.util
    :refer
    [<p <ajax <seq<! js-seq normalize-css load-style! put!close! chan?
     parse-json-or-nil log page-ready render dom->clj next-tick]]
   [devtools.core :as devtools]
   [cljs.core.async :as async :refer [>! <! chan put! take! timeout close! pipe tap mult]]
   [cognitect.transit :as transit]))

(when js/window.applicationCache
  (aset js/window.applicationCache "onupdateready" #(js/location.reload)))
(defonce dev-tools (devtools/install!))
(defonce empty-choice "· · ·")
(defn clj->json [s] (transit/write (transit/writer :json) s))
(defn json->clj [s] (transit/read (transit/reader :json) s))
(defn third [col] (nth col 2))

(defn <chan-seq [arr] (async/reduce conj nil (async/merge arr)))
(defn fourth-first [[v _ _ k]] [k v])
(defn <localforage [k] (<p (.getItem js/localforage k)))
(defn <localforage! [k v] (<p (.setItem js/localforage k v)))

(defn to-map
  [o]
  (cond
    (map? o) o
    (sequential? o) (zipmap (range) o)
    :else {}))
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

(defn timestamp->isostring [i] (.toISOString (js/Date. i)))
(defn str->timestamp [s] (.valueOf (js/Date. s)))

(defn throttle "Limit how often a function (without arguments) is called"
  ([f t] (let [prev-t (atom 0)
               running (atom false)
               scheduled (atom false)]
           (log 'here)
           (fn []
             (if @running
               (reset! scheduled true)
               (do
                 (reset! running true)
                 (go-loop []
                   (let [now (js/Date.now)
                         delta-t (- now @prev-t)]
                     (reset! prev-t now)
                     (when (< delta-t t)
                       (<! (timeout (- t delta-t))))
                     (let [result (f)]
                       (when (chan? result)
                         (<! result)))
                     (if @scheduled
                       (do (reset! scheduled false)
                           (recur))
                       (reset! running false))))))))))
(defn tap-chan [m] (let [c (chan)] (tap m c) c)) 
