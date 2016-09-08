(ns solsort.fmtools.kvdb
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require
   [solsort.fmtools.db :refer [warn]]
   [solsort.util :refer [<p log]]
   [solsort.fmtools.localforage :as lf]
   [cljs.core.async :as async :refer [>! <! chan put! take! timeout close! pipe]]))
(defn create-websql []
  (defonce websql (js/openDatabase "kvdb" "0.0.1" "Key-Value Database" 5000000))
  (.transaction
   websql
   (fn [transaction]
     (.executeSql transaction "CREATE TABLE IF NOT EXISTS KVDB (k PRIMARY KEY, v)"))))

(create-websql)

(defn <all []
  (let [c (chan)
        start-time (js/Date.now)]
    (.readTransaction
     websql
     (fn [transaction]
       (.executeSql
        transaction
        "SELECT * FROM KVDB"
        #js []
        (fn [_ result]
          (let [o (into {}
                        (for [i (range (.-length (.-rows result)))]
                          (let [o (aget (.-rows result) i)]
                            [(.-k o) (.-v o)])))]
            (log '<all (.-length (.-rows result)) (- (js/Date.now) start-time))
            (put! c o)))
        (fn [& args]
          (log 'sql-error args)
          (close! c)))))
    c))
(defn clear []
  (.transaction websql #(.executeSql % "DROP TABLE KVDB") create-websql)
  (create-websql)
  (go))
(defonce puts (atom {})) ; puts not started written yet
(defonce all-puts (atom {})) ; puts in progress
(defonce putchans (atom []))

(defn put [k v]
  (swap! puts assoc k v)
  (swap! all-puts assoc k v))
(defn <sync []
  (if (empty? @puts)
    (go)
    (let [c (chan)]
      (swap! putchans conj c)
      c)))
(defn <put [k v]
  (put k v)
  (<sync))
(defn <get [k]
  (if (contains? all-puts k)
    (get all-puts k)
    (let [c (chan)]
      (.readTransaction
       websql
       (fn [transaction]
         (.executeSql
          transaction
          "SELECT (k,v) FROM KVDB WHERE k=?"
          #js [k]
          (fn [_ result]
            (let [o (into {}
                          (for [i (range (.-length (.-rows result)))]
                            (let [o (aget (.-rows result) i)]
                              [(aget o "k") (aget o "v")])))]
              (put! c o)))
          (fn [& args]
            (warn 'sql-error args)
            (close! c)))))
      c)))
(defn <write-all []
  (let [put-data @puts
        listeners @putchans
        c (chan)
        start-time (js/Date.now)]
    (log 'write-all (count put-data))
    (reset! all-puts put-data)
    (reset! puts {})
    (.transaction
     websql
     (fn [transaction]
       (doall
        (map
         #(.executeSql
           transaction
           "REPLACE INTO KVDB VALUES (?,?)"
           (clj->js %))
         put-data)))
     (fn [err]
       (warn "DB-err" err)
       (close! c))
     #(put! c true))
    (go (<! c)
        (doall (for [c listeners] (close! c)))
        (reset! putchans [])
        (log 'write-done (- (js/Date.now) start-time)))))
(defn <writer []
  (go
    (if (empty? @puts)
      (<! (timeout 200))
      (<! (<write-all)))))
;(go (log (:settings (<! (<all)))))

(defonce write-loop
  (go-loop []
    (<! (<writer))
    (recur)))
