(ns solsort.fmtools.disk-sync
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]]
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
   [solsort.fmtools.db :refer [db db! api-db]]
   [solsort.fmtools.kvdb :as kvdb]
   [devtools.core :as devtools]
   [cljs.pprint]
   [cognitect.transit :as transit]
   [solsort.toolbox.misc :refer [<blob-url]]
   [solsort.toolbox.transit :refer [clj->json json->clj]]
   [solsort.util
    :refer
    [<p <ajax <seq<! js-seq normalize-css load-style! put!close!
     parse-json-or-nil log page-ready render dom->clj next-tick]]
   [reagent.core :as reagent :refer []]
   [cljs.reader :refer [read-string]]
   [clojure.data :refer [diff]]
   [clojure.string :as string :refer [replace split blank?]]
   [cljs.core.async :as async :refer [>! <! chan put! take! timeout close! pipe]]))

(defonce disk-db (atom {}))
(defonce needs-sync (atom {}))
(defn save-obj! [o]
  (when-not (= o (get @disk-db (:id o)))
    (swap! disk-db assoc (:id o) o)
    (swap! needs-sync assoc (:id o) o)))

(defn- <sync-to-disk! []
  (go
    (when-not (empty? @needs-sync)
      (db! [:ui :disk] (inc (db [:ui :disk])))
      (let [objs @needs-sync]
        (reset! needs-sync {})
        (loop [[k o] (first objs)
               objs (rest objs)]
          (kvdb/put (prn-str (:id o)) (prn-str o))
          (when-not (empty? objs)
            (recur (first objs) (rest objs)))))
      (<! (kvdb/<sync))
      (db! [:ui :disk] (dec (db [:ui :disk]))))))
(defonce disk-writer
  (go-loop []
    (<! (<sync-to-disk!))
    (<! (timeout 100))
    (recur)))

(defn <restore "load current template/reports from disk"
  []
  (go
    (let [o (->>
             (<! (kvdb/<all))
             (map second)
             (map read-string)
             (map (fn [o] [(:id o) o]))
             (into {}))]
      (reset! disk-db o)
      (db! [:obj] @disk-db)
      (reset!
       api-db
       (into {}
             (remove
              (fn [_ o] (:local o))
              @disk-db)))
      (log 'restore (count (db [:obj])))
      )))
