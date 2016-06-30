(ns solsort.fmtools.db
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]]
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
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
   [clojure.walk :refer [keywordize-keys]]
   [re-frame.core :as re-frame
    :refer [register-sub subscribe register-handler
            dispatch dispatch-sync]]
   [clojure.string :as string :refer [replace split blank?]]
   [cljs.core.async :as async :refer [>! <! chan put! take! timeout close! pipe]]))

(register-sub
 :db
 (fn  [db [_ & path]]
   (reaction
    (if path
      (get-in @db path)
      @db))))

(register-handler
 :db
 (fn  [db [_ & path]]
   (let [value (last path)
         path (butlast path)]
     (if path
       (assoc-in db path value)
       value))))

(register-handler
 :raw-report
 (fn  [db [_ report data role]]
   (dispatch [:sync-to-disk])
   (-> db
       (assoc-in [:reports (:ReportGuid report)] report)
       (assoc-in [:raw-report (:ReportGuid report)]
                 {:report report
                  :data data
                  :role role}))))
(register-sub
 :ui (fn  [db [_ id]]  (reaction (get-in @db [:ui id]))) )
(register-handler
 :ui (fn  [db  [_ id data]] (assoc-in db [:ui id] data)))

(register-sub
 :templates (fn  [db]  (reaction (keys (get @db :templates {})))))
(register-sub
 :template (fn  [db [_ id]]  (reaction (get-in @db [:templates id] {}))))
(register-handler
 :template
 (fn  [db  [_ id template]]
   (dispatch [:sync-to-disk])
   (assoc-in db [:templates id] template)))

(register-sub
 :area-object (fn  [db [_ id]]  (reaction (get-in @db [:objects id] {}))))
(register-handler
 :area-object
 (fn  [db  [_ obj]]
   (let [id (:ObjectId obj)
         obj (into (get-in db [:objects id] {}) obj)
         area-guid (:AreaGuid obj)
         parent-id (:ParentId obj)
         db
         (if (zero? parent-id)
           (-> db
               (assoc-in [:objects :root :children area-guid] true)
               (assoc-in [:objects area-guid]
                         (or (get-in db [:objects area-guid])
                             {:ParentId 0
                              :AreaGuid area-guid
                              :ObjectId area-guid
                              :ObjectName (str (:AreaName obj))}))
               (assoc-in [:objects area-guid :children id] true)
                                        ; todo add in-between-node
               )
           (assoc-in db [:objects parent-id :children id] true))]
     (assoc-in db [:objects id] obj))))
