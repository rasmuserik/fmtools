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


(declare db)
(defn db-raw [& path]
  (if path
    (reaction (get @(apply db (butlast path)) (last path)))
    (subscribe [:db])))
(def db
  "memoised function, that returns a subscription to a given path into the application db"
  (memoize db-raw))
(defn db! "Write a value into the application db" [& path]
  (dispatch (into [:db] path)))
(defn db-sync! "Write a value into the application db" [& path]
  (dispatch-sync (into [:db] path)))

(defn obj [id]
  (or @(db :obj id) {:id id}))
(defn obj! [o]
  (if (:id o)
    (db-sync! :obj (:id o) (into (or @(db :obj (:id o)) {}) o))
    (log 'no-id o))
  o)

(register-sub :db
 (fn  [db [_ & path]]
   (reaction
    (if path
      (get-in @db path)
      @db))))
(register-handler :db
                  (fn  [db [_ & path]] (let [value (last path) path (butlast path)] (if path (assoc-in db path value) value))))

(register-handler :raw-report
 (fn  [db [_ report data role]]
   (-> db
       (assoc-in [:reports (ReportGuid report)] report)
       (assoc-in [:raw-report (ReportGuid report)]
                 {:report report
                  :data data
                  :role role}))))
(register-sub :ui
              (fn  [db [_ id]]  (reaction (get-in @db [:ui id]))) )
(register-handler :ui
                  (fn  [db  [_ id data]] (assoc-in db [:ui id] data)))

(register-sub :templates
              (fn  [db]  (reaction (keys (get @db :templates {})))))
(register-sub :template
              (fn  [db [_ id]]  (reaction (get-in @db [:templates id] {}))))
(register-handler :template
 (fn  [db  [_ id template]]
   (assoc-in db [:templates id] template)))

(register-sub :area-object
              (fn  [db [_ id]]  (reaction (get-in @db [:objects id] {}))))
(register-handler :area-object
 (fn  [db  [_ obj]]
   (let [id (ObjectId obj)
         obj (into (get-in db [:objects id] {}) obj)
         area-guid (AreaGuid obj)
         parent-id (ParentId obj)
         obj (assoc obj "ParentId" (if (zero? parent-id) area-guid parent-id))
         db
         (if (zero? parent-id)
           (-> db
               (assoc-in [:objects :root :children area-guid] true)
               (assoc-in [:objects area-guid]
                         (or (get-in db [:objects area-guid])
                             {"ParentId" 0
                              "AreaGuid" area-guid
                              "ObjectId" area-guid
                              "ObjectName" (str (AreaName obj))}))
               (assoc-in [:objects area-guid :children id] true)
                                        ; todo add in-between-node
               )
           (assoc-in db [:objects parent-id :children id] true))]
     (assoc-in db [:objects id] obj))))

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

(defn- logexpand [id]
  (let [obj @(db :obj id)]
    [id (assoc obj :children (into {} (map logexpand (:children obj))))]))
(defn logdb
  ([k] (log (second (logexpand k))) nil)
  ([] (logdb :root)))
(logdb)
(log 'test)
