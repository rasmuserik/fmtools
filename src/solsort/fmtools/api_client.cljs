(ns solsort.fmtools.api-client
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require
   [solsort.fmtools.definitions :refer
    [line-types part-types field-types
     ReportTemplateTable ReportTemplateFields ReportTemplateParts
     ReportTables ReportTable Areas FieldGuid ReportFields Objects
     AreaGuid
     LineType PartType Name ReportGuid ReportName FieldType DisplayOrder PartGuid]]
   [solsort.fmtools.util :refer [third to-map delta empty-choice <chan-seq <localforage fourth-first]]
   [solsort.util
    :refer
    [<p <ajax <seq<! js-seq normalize-css load-style! put!close!
     log page-ready render dom->clj next-tick]]
   [re-frame.core :as re-frame
    :refer [register-sub subscribe register-handler
            dispatch dispatch-sync]]
   [cljs.core.async :as async :refer [>! <! chan put! take! timeout close! pipe]]))

(defn <api [endpoint]
  (<ajax (str "https://"
              "fmtools.solsort.com/api/v1/"
                                        ;"app.fmtools.dk/api/v1/"
                                        ;(js/location.hash.slice 1)
                                        ;"@fmproxy.solsort.com/api/v1/"
              endpoint)
         :credentials true))

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
      (doall
       (for [object objects]
         (let [object (assoc object "AreaName" (Name area))]
           (dispatch-sync [:area-object object])
           ))))
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
      (log 'loaded-reports)
      )))

(defn <fetch []
  (go
    (dispatch-sync [:db :loading true])
    (<! (<chan-seq [(<load-reports)
                 (<load-templates)
                 (<load-objects)
                    #_(go (let [user (<! (<api "User"))] (dispatch [:user user])))]))
    (dispatch-sync [:db :loading false])
    ))
