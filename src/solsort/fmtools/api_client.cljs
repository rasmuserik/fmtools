(ns solsort.fmtools.api-client
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require
   [solsort.fmtools.definitions :refer
    [trail-types full-sync-types
     ]]
   [solsort.fmtools.util :refer [str->timestamp timestamp->isostring]]
   [solsort.fmtools.db :refer [db db! api-db]]
   [solsort.fmtools.load-api-data :refer [<load-api-db! init-root! <api]]
   [solsort.fmtools.data-index :refer [update-entry-index!]]
   [solsort.fmtools.disk-sync :as disk]
   [clojure.set :as set]
   [solsort.util :refer [log]]
   [cljs.core.async :as async :refer [>!]]))

(defn api-to-db! []
  (db! [:obj] (into (db [:obj]) @api-db)))

(defn <update-state []
  (go
    (let [prev-sync (db [:obj :state :prev-sync] "2000-01-01")
          trail (->
                 (<! (<api (str "AuditTrail?trailsAfter=" prev-sync)))
                 (get "AuditTrails"))
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
           {:prev-sync last-update
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
        (<! (disk/<save-form))
        (update-entry-index!)
        (db! [:loading] false))))
(defn <fetch [] "conditionally update db"
  (go
    (<! (<update-state))
    (when-not (empty? (set/intersection full-sync-types (updated-types)))
      (<! (<do-fetch)))))
