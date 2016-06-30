(ns solsort.fmtools.disk-sync ; ##
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]]
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
   [solsort.fmtools.util :refer [clj->json json->clj third to-map delta empty-choice <chan-seq <localforage fourth-first]]
   [solsort.fmtools.db]
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
   [clojure.walk :refer [keywordize-keys]]
   [re-frame.core :as re-frame
    :refer [register-sub subscribe register-handler
            dispatch dispatch-sync]]
   [clojure.string :as string :refer [replace split blank?]]
   [cljs.core.async :as async :refer [>! <! chan put! take! timeout close! pipe]]))

;; we are writing the changes to disk.
;; The structure of a json object like
;; `{a: 1, b: ['c', {d: 'e'}]}` is:
;;
;; - 0 {a: 1, b: '\u00011'}
;; - 1 ['c', '\u00012']
;; - 2 {d: 'e'}
;;
;; if first char of string has ascii value < 4 it is prefixed with "\u0000"
;;
;; references in db are "\u0001" followed by id
;;
;; keywords are "\u0002" followed by keyword

(defonce prev-id (atom nil))
(defonce sync-in-progress (atom false))
(defonce diskdb (atom {}))

(defn esc-str [s] (if (< (.charCodeAt s 0) 32) (str "\u0001" s) s))
(defn optional-escape-string [o] (if (string? o) (esc-str o) o))
(defn unescape-string [s] (case (.charCodeAt s 0) 1 (.slice s 1) s))
(defn optional-unescape-string [o] (if (string? o) (unescape-string o) o))
(defn next-id [] (swap! prev-id inc) (str "\u0002" @prev-id))
(defn is-db-node [s] (and (string? s) (= 2 (.charCodeAt s))))
(defn save-changes
  "(value id key) -> (result-value, changes, deleted, key)"
  [value id k]
  (go
    (if (= value :keep-in-db)
      [id {} [] k]
      (let
          [db-str (and id (<! (<localforage id)))
           db-map (read-string (or db-str "{}"))
           value-map (to-map value)
           all-keys (distinct (concat (keys db-map) (keys value-map)))
           save-fn #(save-changes (get value-map % :keep-in-db) (db-map %) %)
           children (<! (<chan-seq (map save-fn all-keys)))
           new-id (if (coll? value) (next-id) nil)
           saves (if new-id {new-id (into {} (map fourth-first children))} {})
           saves (apply merge saves (map second children))
           deletes (apply concat (if db-str [id] []) (map third children))]
        [(or new-id (optional-escape-string value)) saves deletes k]))))

(defn <load-db-item [k]
  (go
    (let [v (read-string (<! (<localforage k)))
          v (map
             (fn [[k v]]
               (go
                 [k (if (is-db-node v)
                      (<! (<load-db-item v))
                      (optional-unescape-string v))]))
             v)
          v (into {}  (<! (<chan-seq v)))
          v (if (every? #(and (integer? %) (<= 0 %)) (keys v))
              (let [length  (inc (apply max (keys v)))]
                (into [] (map v (range length))))
              v)]
      v)))

(defn <load-db []
  (when @sync-in-progress
    (throw "<load-db sync-in-progress error"))
  (go
    (reset! sync-in-progress true)
    (let [root-id (<! (<localforage "root-id"))
          result (if root-id (<! (<load-db-item root-id)) {})]
      (reset! diskdb result)
      (reset! sync-in-progress false)
      result)))

(defn <to-disk
  [db]
  (go
    (let [changes (delta @diskdb db)
          id (or (<! (<p (.getItem js/localforage "root-id"))) " 0")
          prev-id (reset! prev-id (js/parseInt (.slice id 1)))
          [root-id chans deletes] (<! (save-changes changes id nil))]
      (log 'to-disk db changes)
      (<! (<chan-seq (for [[k v] chans]
                       (let [v (into {} (filter #(not (nil? (second %))) v))]
                         (<p (.setItem js/localforage k (prn-str v)))))))
      (<! (<p (.setItem js/localforage "root-id" root-id)))
      (<! (<chan-seq (for [k deletes] (<p (.removeItem js/localforage k)))))
      (reset! diskdb db))))

(defn <sync-db [db]
  (log 'sync-start)
  (go
    (if @sync-in-progress
      (log 'in-progress)
      (do
        (reset! sync-in-progress true)
        (<! (<to-disk db))
        (reset! sync-in-progress false)))))

#_(<sync-db {(js/Math.random) [:a :b] :c [:d]})
(defonce sync-runner
  (go
    (log 'loading-db)
    (dispatch-sync [:ui (<! (<load-db))])
    (log 'loaded-db)
    (loop []
      (log 'start-sync)
      (let [t0 (js/Date.now)]
        (<! (<sync-db @(subscribe [:db :ui])))
        (log 'sync-time (- (js/Date.now) t0)))
      (<! (timeout 10000))
      (recur)
      )
    ))
(log 'ui @(subscribe [:db :ui]))

;; re-frame :sync-to-disk
(register-handler
 :sync-to-disk
 (fn  [db]
   #_(js/localStorage.setItem "db" (js/JSON.stringify (clj->json db)))
   #_(<sync-db db)
   db))

(register-handler
 :restore-from-disk
 (fn  [db]
   #_(json->clj (js/JSON.parse (js/localStorage.getItem "db")))
   #_(go (dispatch [:db (<! (<load-db))]) )
   #_(go (log 'db-restore [:db (<! (<load-db))]) )
   db))

(defonce restore (dispatch [:restore-from-disk]))

(defn <save-form
  "write the current data in the database to disk"
  []
  (go
    (log 'not-implemented-yet)))
