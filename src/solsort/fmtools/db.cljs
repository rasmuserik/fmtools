(ns solsort.fmtools.db
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]]
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
   [solsort.fmtools.definitions :refer
    [ReportGuid AreaGuid ParentId ObjectId ObjectName AreaName
     ]]
   [solsort.fmtools.util :refer [clj->json json->clj third to-map delta empty-choice]]
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
   [re-frame.core :as re-frame
    :refer [register-sub subscribe register-handler
            dispatch dispatch-sync]]
   [clojure.string :as string :refer [replace split blank?]]
   [cljs.core.async :as async :refer [>! <! chan put! take! timeout close! pipe]]))


(declare db-impl)
(defn db-raw [& path]
  (if path
    (reaction (get @(apply db-impl (butlast path)) (last path)))
    (subscribe [:db])))
(def db-impl
  "memoised function, that returns a subscription to a given path into the application db"
  (memoize db-raw))
(def db db-impl)
(defn db! "Write a value into the application db" [& path]
  (dispatch (into [:db] path))
  (last path))
(defn db-sync! "Write a value into the application db" [& path]
  (dispatch-sync (into [:db] path))
  (last path))
(defn xb
  ([] (xb []))
  ([path] @(apply db-impl path))
  ([path default]
   (let [val @(apply db-impl path)]
     (if (nil? val) default val))))

(defn obj [id] (xb [:obj id] {:id id}))
(defn obj! [o]
  (if (:id o)
    (db-sync! :obj (:id o) (into (xb [:obj (:id o)] {}) o))
    (log nil 'no-id o)))

(register-sub :db
 (fn  [db [_ & path]]
   (reaction
    (if path
      (get-in @db path)
      @db))))
(register-handler :db
                  (fn  [db [_ & path]] (let [value (last path) path (butlast path)] (if path (assoc-in db path value) value))))

(register-handler :add-image
                  (fn  [db  [_ id img-url]]
                    (assoc-in db id
                              (conj (remove #{img-url}
                                     (get-in db id))
                                    img-url))))
(register-handler :remove-image
                  (fn  [db  [_ id img-url]]
                    (assoc-in db id
                              (remove #{img-url} (get-in db id)))))
