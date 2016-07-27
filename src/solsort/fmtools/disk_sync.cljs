(ns solsort.fmtools.disk-sync
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]]
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
   [solsort.fmtools.util :refer [clj->json json->clj third to-map delta
                                 empty-choice <chan-seq fourth-first]]
   [solsort.fmtools.db :refer [db db! api-db]]
   [devtools.core :as devtools]
   [cljs.pprint]
   [cljsjs.localforage]
   [cognitect.transit :as transit]
   [solsort.misc :refer [<blob-url]]
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
  (when-not (= o(get @disk-db (:id o)))
    (swap! disk-db assoc (:id o) o)
    (swap! needs-sync assoc (:id o) o)))

(def localforage-db
  (.createInstance js/localforage
                   #js {
                        :name "JsonData"
                        :version "0.1"
                        :storeName "JsonData"
                        :description "JSON data store"
                        }))

(def localforage-images
  (.createInstance js/localforage
                   #js {
                        :name "ImageData"
                        :version "0.1"
                        :storeName "ImageData"
                        :description "Image store"
                        }))

(defn <localforage-db [k] (<p (.getItem localforage-db k)))
(defn <localforage-db! [k v] (<p (.setItem localforage-db k v)))

(defn <localforage-images [k] (<p (.getItem localforage-images k)))
(defn <localforage-images! [k v] (<p (.setItem localforage-images k v)))

(defn- <sync-to-disk! []
  (go
    (when-not (empty? @needs-sync)
      (log 'writing-to-disk)
      (let [objs @needs-sync]
        (log 'syncing-to-disk (count objs))
        (reset! needs-sync {})
        (loop [[k o] (first objs)
               objs (rest objs)]
          (<! (<localforage-db! (prn-str (:id o)) (clj->json o)))
          (when-not (empty? objs)
            (recur (first objs) (rest objs)))))
      (log 'writing-done))))
(defonce disk-writer
  (go-loop []
    (<! (<sync-to-disk!))
    (<! (timeout 100))
    (recur)))

(defn <restore
  "load current template/reports from disk"
  []
  (let [c (chan)]
    (reset! disk-db {})
    (.iterate localforage-db
     (fn [v]
        (try
          (let [o (json->clj v)]
           (swap! disk-db assoc (:id o) o))
          (catch js/Object e (js/console.log e)))
       js/undefined)
     (fn []
       (db! [:obj] @disk-db)
       (reset!
        api-db
        (into {}
              (remove
               (fn [_ o] (:local o))
               @disk-db)))
       (log 'restore (count (db [:obj])))
       (close! c)))
    c))
