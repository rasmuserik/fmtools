(ns solsort.fmtools.api-client
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require
   [solsort.fmtools.definitions :refer [trail-types full-sync-types]]
   [solsort.fmtools.util :refer [str->timestamp timestamp->isostring]]
   [solsort.fmtools.db :refer [db db! api-db]]
   [solsort.fmtools.load-api-data :refer [<load-api-db! init-root! <api]]
   [solsort.fmtools.data-index :refer [update-entry-index!]]
   [solsort.fmtools.disk-sync :as disk]
   [clojure.set :as set]
   [solsort.util :refer [log <ajax <chan-seq]]
   [cljs.core.async :as async :refer [>! timeout]]))


(defn api-to-db! []
  (db! [:obj] (into (db [:obj]) @api-db)))

(defn <update-state []
  (go
    (let [prev-sync (db [:obj :state :prev-sync] "2000-01-01")
          trail (->
                 (<! (<api (str "AuditTrail?trailsAfter=" prev-sync)))
                 (get "AuditTrails"))
          ;_ (log 'trail trail)
          trail (into (db [:obj :state :trail] #{})
                      (map #(assoc % :type (trail-types (get % "AuditType"))) trail))
          last-update (->> trail
                           (map #(get % "CreatedAt"))
                           (reduce max))
          last-update (max last-update prev-sync)
          last-update (if last-update
                        ; TODO: as trailsAfter include event at timestamp, instead of after timestamp, we increment last-update timestamp here. This should probably be fixed in the api, and then the workaround here should be removed
                        (.slice
                         (timestamp->isostring (inc (str->timestamp last-update)))
                         0 -1)
                        prev-sync)]
      (db! [:obj :state]
           {:id :state
            :prev-sync last-update
            :trail trail}))))
(defn updated-types []
  (into #{} (map :type (db [:obj :state :trail]))))

(init-root!)
(update-entry-index!)

(defn <do-fetch "unconditionally fetch all templates/areas/..."
  []
  (if (db [:loading])
    (go nil)
    (go (db! [:loading] true)
        (<! (<load-api-db!))
        (api-to-db!)
        (db! [:obj :state :trail]
             (filter #(nil? (full-sync-types (:type %))) (db [:obj :state :trail])))
        (update-entry-index!)
        (db! [:loading] false))))
(defn update-part-trail! [trail]
  (let [id (get trail "PrimaryGuid")
        o (db [:obj id])
        updated (into o {"Performed" (get trail "FieldBoolean")
                         "Remarks" (get trail "FieldString")
                         "Amount" (get trail "FieldInteger")})]
    (when (= o updated)
      (let [updated (dissoc updated :local)]
        (swap! api-db assoc id updated)
        (db [:obj id] updated)))))
(defn <fetch [] "conditionally update db"
  (go
    (<! (<update-state))
    (when-not (empty? (set/intersection full-sync-types (updated-types)))
      (<! (<do-fetch)))
    (when-not (empty? (db [:obj :state :trail]))
      (log 'handle-trail (db [:obj :state :trail]))
      (doall
       (for [o (db [:obj :state :trail])]
         (case (:type o)
           :part-changed (update-part-trail! o)
           ;; TODO field-changed
           (log (:type o) 'not 'handled)
           )
         ))
      (db! [:obj :state :trail] #{}))
    ))

(defonce needs-sync (atom {}))
(defn sync-obj! [o]
  (when (and (:local o)
             (:type o))
    (swap! needs-sync assoc (:id o) o)))

(defonce field-sync-fields
  #{"FieldGuid" "PartGuid" "TemplateFieldGuid" "FieldId"
    "StringValue1" "StringValue2" "BooleanValue1" "BooleanValue2"
    "IntegerValue1" "IntegerValue2" "DoubleValue1" "DoubleValue2"
    "DateTimeValue1" "TimeSpanValue1" ;"ModifiedAt" "ModifiedBy"
    })
(defn <sync-field! [o]
  (let [payload (clj->js (into {} (filter #(field-sync-fields (first %)) (seq o))))]
    (<ajax "https://fmproxy.solsort.com/api/v1/Report/Field"
           :method "PUT" :data payload)))
(defonce part-sync-fields
  #{"PartGuid" "ReportGuid" "Performed" "Remarks" "Amount" "ObjectId"})
(defn <sync-part! [o]
  (go
    (let [payload (clj->js (into {} (filter #(part-sync-fields (first %)) (seq o))))]
      (<! (<ajax "https://fmproxy.solsort.com/api/v1/Report/Part"
              :method "PUT" :data payload)))))
(defn <sync-to-server! []
  (go
    (let [objs (vals @needs-sync)]
     (when-not (empty? objs)
       (log 'sync-to-server! (map :type objs))
       (<!
        (<chan-seq
         (doall (for [o objs]
                  (do
                    (case (:type o)
                          :field-entry (<sync-field! o)
                          :part-entry (<sync-part! o)
                          (go (log 'no-sync-type o))))))))
       (reset! needs-sync {}) ; TODO: remove this line, when update through audittrail works
       )
     )))
(defn <sync! []
  (go
    (when js/navigator.onLine
      (go
        (<! (<sync-to-server!))
        (<! (<fetch))))
    (<! (timeout 5000)))
  )
(defonce -sync-loop
  (go-loop []
    (<! (<sync!))
    (recur)))
