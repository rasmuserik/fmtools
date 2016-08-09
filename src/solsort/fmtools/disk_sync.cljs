(ns solsort.fmtools.disk-sync
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]]
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
   [solsort.fmtools.util :refer [clj->json json->clj third to-map delta empty-choice <chan-seq <localforage! <localforage fourth-first]]
   [solsort.fmtools.db :refer [db db!]]
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

(def disk (atom {}))

(defn <save-form
  "write the current data in the database to disk"
  []
  (go
    (log 'save-form)
    (<! (<localforage! (prn-str [:obj]) (clj->json (db [:obj]))))))

(defn <restore-form
  "load current template/reports from disk"
  []
  (let [c (chan)]
    (js/localforage.iterate
     (fn [v k i]
       (try
         (let [path (read-string k)
               val (json->clj v)]
           (log 'restore i path)
           (db! path val)
           (swap! disk assoc-in path val)
           (db! (concat [:disk] path) val))
         (catch js/Object e (js/console.log e)))
       js/undefined)
     #(close! c))
    c))

(defn find-changes [a b prefix]
  (if (= a b)
    []
    (if (map? a)
      (if (map? b)
        (let [ks (distinct (concat (keys a) (keys b)))]
          (apply concat (map #(find-changes (a %) (b %) (conj prefix %)) ks)))
        (apply concat [[prefix b]] (map #(find-changes (second %) nil (conj prefix (first %))) a))
        )
      (if (map? b)
        (apply concat (if (nil? a) [] [[prefix nil]])
               (map #(find-changes nil (second %) (conj prefix (first %))) b))
        [[prefix b]])
      )))

(defn handle-changes! [path db]
  (swap!
   disk
   (fn [disk]
     (let [changes (find-changes (get-in disk path) db (into [] path))]
       (doall (map (fn [[a b]] (<localforage! (prn-str a) (clj->json b))) changes))
       (reduce (fn [disk [path val]](assoc-in disk path val)) disk changes))
       )))

(defn watch-changes! [& path]
  (ratom/run!
   (handle-changes! path (db path))))
