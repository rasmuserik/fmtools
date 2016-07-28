(ns solsort.fmtools.changes
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]]
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
   [solsort.fmtools.util :refer
    [clj->json json->clj third to-map delta empty-choice <chan-seq <localforage!
     <localforage fourth-first throttle tap-chan]]
   [solsort.fmtools.db :refer [db db! db-sync!]]
   [devtools.core :as devtools]
   [cljs.pprint]
   [cljsjs.localforage]
   [cognitect.transit :as transit]
   [solsort.misc :refer [<blob-url]]
   [solsort.util
    :refer
    [chan? <p <ajax <seq<! js-seq normalize-css load-style! put!close!
     parse-json-or-nil log page-ready render dom->clj next-tick]]
   [reagent.core :as reagent :refer []]
   [cljs.reader :refer [read-string]]
   [clojure.data :refer [diff]]
   [clojure.string :as string :refer [replace split blank?]]
   [cljs.core.async :as async :refer [>! <! chan put! take! timeout close! pipe mult tap]]))

(defonce prev-objs (atom #{}))
(defonce change-chan (chan))
(defonce changes (mult change-chan))
(defn- handle-changes!-impl []
  (go
   (let [objs (into #{} (vals @(db :obj)))
         changes (remove @prev-objs objs)]
     (when-not (empty? changes)
      (>! change-chan changes))
     (reset! prev-objs objs))))
(def handle-changes! (throttle handle-changes!-impl 1000))

(defonce init
    (do
      (let [c (tap-chan changes)]
        (go-loop []
          (js/console.log 'change-loop (<! c))
          (recur)
          ))
      (ratom/run!
      @(db :obj)
      (handle-changes!))))
