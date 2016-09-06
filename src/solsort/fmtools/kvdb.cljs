(ns solsort.fmtools.kvdb
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require
   [solsort.util :refer [<p log]]
   [solsort.fmtools.localforage :as lf]
   [cljs.core.async :as async :refer [>! <! chan put! take! timeout close! pipe]]))
(defonce use-websql true)
(if use-websql
 (do
   (defn create-websql []
     (defonce websql (js/openDatabase "kvdb" "0.0.1" "Key-Value Database" 5000000))
     (.transaction
      websql
      (fn [transaction]
        (.executeSql transaction "CREATE TABLE IF NOT EXISTS KVDB (k PRIMARY KEY, v)")
        )))

   (create-websql)

   (defn <put [k v]
     (.transaction
      websql
      (fn [transaction]
        (.executeSql
         transaction
         "REPLACE INTO KVDB VALUES (?,?)"
         #js[k v]))
      #(log 'DB-err %)
      )
     (go)
     )
   (defn <all []
     (let [c (chan)]
       (.transaction
        websql
        (fn [transaction]
          (.executeSql
           transaction
           "SELECT * FROM KVDB"
           #js[]
           (fn [_ result]
             (let [o (into {}
                           (for [i (range (.-length (.-rows result)))]
                             (let [o (aget (.-rows result) i)]
                               [(.-k o) (.-v o)]
                               )))]
               (log (.-length (.-rows result)))
               (put! c o)))
           (fn [& args]
             (log 'sql-error args)
             (close! c))
           )))
       c))
   (defn clear []
     (.transaction websql #(.executeSql % "DROP TABLE KVDB") create-websql)
     (go)
     )
   )
 (do ; not using websql

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
))
