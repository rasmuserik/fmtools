(ns solsort.fmtools.api-client
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require
   [solsort.fmtools.definitions :refer
    [trail-types full-sync-types
     line-types part-types field-types
     ReportTemplateTable ReportTemplateFields ReportTemplateParts
     ReportTables ReportTable Areas FieldGuid ReportFields Objects
     AreaGuid
     LineType PartType Name ReportGuid ReportName FieldType DisplayOrder PartGuid]]
   [solsort.fmtools.util :refer [third to-map delta empty-choice <chan-seq <localforage fourth-first]]
   [solsort.fmtools.db :refer [db db! db-sync!]]
   [solsort.fmtools.disk-sync :as disk]
   [clojure.set :as set]
   [solsort.util
    :refer
    [<p <ajax <seq<! js-seq normalize-css load-style! put!close!
     log page-ready render dom->clj next-tick]]
   [re-frame.core :as re-frame
    :refer [register-sub subscribe register-handler
            dispatch dispatch-sync]]
   [cljs.core.async :as async :refer [>! <! chan put! take! timeout close! pipe]]))

(defn timestamp->isostring [i] (.toISOString (js/Date. i)))
(defn str->timestamp [s] (.valueOf (js/Date. s)))

(defn <api [endpoint]
  (<ajax (str "https://"
              "fmtools.solsort.com/api/v1/"
                                        ;"app.fmtools.dk/api/v1/"
                                        ;(js/location.hash.slice 1)
                                        ;"@fmproxy.solsort.com/api/v1/"
              endpoint)
         :credentials true))

(defn <update-state []
  (go
    (let [prev-sync (or @(db :state :prev-sync) "2000-01-01")
          trail (->
                 (<! (<api (str "AuditTrail?trailsAfter=" prev-sync)))
                 (get "AuditTrails"))
          trail (into (or @(db :state :trail) #{})
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
      (db-sync! :state
                {:prev-sync last-update
                 :trail trail}))))

(defn updated-types []
  (into #{} (map :type @(db :state :trail))))

(defn <load-template [template-id]
  (go
    (let [template (<! (<api (str "ReportTemplate?templateGuid="
                                  template-id)))
          template (ReportTemplateTable template)
          fields (-> template
                     (ReportTemplateFields)
                     (->>
                      (map #(assoc % "FieldType" (field-types (FieldType %))))
                      (sort-by DisplayOrder)
                      (group-by PartGuid)))
          parts (-> template (ReportTemplateParts))
          parts (map
                 (fn [part]
                   (assoc part :fields
                          (sort-by DisplayOrder
                                   (get fields (PartGuid part)))))
                 (sort-by DisplayOrder parts))
          parts (map #(assoc % "LineType" (or (line-types (LineType %))
                                              (log "invalid-LintType" %))) parts)
          parts (map #(assoc % "PartType" (part-types (PartType %))) parts)]
      (dispatch-sync [:template template-id (assoc template :rows parts)])
      (log 'loaded-template template-id))))
(defn <load-templates []
  (go
    (let [templates (<! (<api "ReportTemplate"))
          template-id (-> templates
                          (get "ReportTemplateTables")
                          (nth 0)
                          (get "TemplateGuid"))]
      (<! (<chan-seq (for [template (get templates "ReportTemplateTables")]
                       (<load-template (get template "TemplateGuid")))))
      (log 'loaded-templates))))

(defn <load-area [area]
  (go
    (let [objects (Objects
                   (<! (<api (str "Object?areaGuid=" (AreaGuid area)))))]
      ;; NB: this is a tad slow - optimisation of [:area-object] would yield benefit
      (doall
       (for [object objects]
         (let [object (assoc object "AreaName" (Name area))]
           (dispatch-sync [:area-object object])))))
    (log 'load-area (Name area))))
(defn <load-objects []
  (go (let [areas (<! (<api "Area"))]
        (log 'areas areas (Areas areas))
        (<! (<chan-seq (for [area (Areas areas)]
                         (<load-area area))))
        (log 'objects-loaded))))
(defn <load-report [report]
  (go
    (let [data (<! (<api (str "Report?reportGuid=" (ReportGuid report))))
          role (<! (<api (str "Report/Role?reportGuid=" (ReportGuid report))))]
      (dispatch-sync [:raw-report report data role])
      (log 'report (ReportName report)))))
(defn <load-reports []
  (go
    (let [reports (<! (<api "Report"))]
      (<! (<chan-seq
           (for [report (ReportTables reports)]
             (<load-report report))))
      (log 'loaded-reports))))
(defn <load-controls []
  (go
    (let [controls (get (<! (<api "ReportTemplate/Control")) "ReportControls")]
      (doall (map #(db! :controls (get % "ControlGuid") %) controls)))))

(defn <do-fetch "unconditionally fetch all templates/areas/..."
  []
  (go (dispatch-sync [:db :loading true])
      (<! (<chan-seq [(<load-objects)
                      (<load-reports)
                      (<load-controls)
                      (<load-templates)
                      (disk/<save-form)
                      #_(go (let [user (<! (<api "User"))] (dispatch [:user user])))]))
      (db-sync! :state :trail
                (filter #(nil? (full-sync-types (:type %))) @(db :state :trail)))
      (dispatch-sync [:db :loading false])))

(defn <fetch [] "conditionally update db"
  (go
    (<! (<update-state))
    (when-not (empty? (set/intersection full-sync-types (updated-types)))
      (<! (<do-fetch)))))
