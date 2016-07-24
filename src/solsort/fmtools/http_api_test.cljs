(ns solsort.fmtools.http-api-test
    (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
    (:require
     [solsort.fmtools.definitions :refer
      [trail-types full-sync-types line-types part-types field-types
       ReportTemplateTable ReportTemplateFields ReportTemplateParts ReportTables
       ReportTable Areas FieldGuid ReportFields Objects AreaGuid LineType
       PartType Name ReportGuid ReportName FieldType DisplayOrder PartGuid
       TemplateGuid ]]
     [solsort.fmtools.api-client :refer
      [<api
       ;; <load-template <load-templates <load-area <load-objects <load-report <load-reports <load-controls
       ]]
     [solsort.util :refer
      [log next-tick <ajax]]
     [solsort.fmtools.util :refer
      [str->timestamp timestamp->isostring]]
     [re-frame.core :as re-frame
      :refer [register-sub subscribe register-handler
              dispatch dispatch-sync]]
     [cljs.test :refer-macros  [deftest is testing run-tests async]]))

(defn without-matching-guid [field-name these those]
  "get field-name value of objects in these that do not have a corresponding field-name in those"
  (loop [ps these res []]
        (if (empty? ps)
          res
          (let [part (first ps)
                part-guid (get part field-name)
                matching (first (filter #(= part-guid (get % field-name)) those))]
            (if matching
              (recur (rest ps) res)
              (recur (rest ps) (conj res part)))))))

#_(deftest get-demo-report-and-objects
  (go
   ;; Requests related to the DEMO report to serve as documentation of the API
   (let [
         ;; Report templates
         report-templates-response (<! (<api (str "ReportTemplate")))
         report-templates-tables (get report-templates-response "ReportTemplateTables")

         ;; Get a shallow description of all reports
         reports-response (<! (<api "Report"))
         report-tables (ReportTables reports-response)

         ;; Reports not in tables
         rnit (without-matching-guid "TemplateGuid" report-templates-tables report-tables)

         ;; Get the full DEMO report including fields, parts and files
         demo (filter #(re-matches #"DEMO.*" (ReportName %)) report-tables)
         demo-report-info (first demo)
         report-guid (ReportGuid demo-report-info)
         template-guid (TemplateGuid demo-report-info)

         demo-report-response (<! (<api (str "Report?reportGuid=" report-guid)))
         demo-report-table (ReportTable demo-report-response)
         demo-report-fields (ReportFields demo-report-table)
         demo-report-files (get demo-report-table "ReportFiles")
         demo-report-parts (get demo-report-table "ReportParts")

         ;; XXX It seems that there are parts not referenced from any fields.  What are they then used for?
         parts-not-in-fields (without-matching-guid "PartGuid" demo-report-parts demo-report-fields)
         fields-not-in-parts (without-matching-guid "PartGuid" demo-report-fields demo-report-parts)

         ;; Get the full report template for this report
         demo-template-response (<! (<api (str "ReportTemplate?templateGuid=" template-guid)))
         demo-template-table (ReportTemplateTable demo-template-response)
         active-area-guid (first (get demo-template-table "ActiveAreaGuids"))

         ;; Get objects associated with this area
         objects-response (<! (<api (str "Object?areaGuid=" active-area-guid)))
         objects (get objects-response "Objects")

         ;; Roles do not seem to be implemented
         report-role-response (<! (<api (str "Report/Role?reportGuid=" report-guid)))
         ]

     ;; Some random assertions
     (is (= 1 (count demo)))
     (is (= "ca968ee8-373e-442a-b173-edbbcfeb4b90" report-guid))
     (is (= "09ec19cb-a46e-44c0-9edf-74cd60554443" template-guid))
     (is (= "0399049a-54e5-4a24-99ce-89cb8bdbf323" active-area-guid))
     (is (= 111 (count objects)))
     (is (= nil (get report-role-response "Rols")))

     ;; DEMO report
     (is (= 250 (count demo-report-fields)))
     (is (= 0 (count demo-report-files)))
     (is (= 93 (count demo-report-parts)))

     (is (= 28 (count parts-not-in-fields)))
     (is (= 0 (count fields-not-in-parts)))

     (log "Report templates tables" report-templates-tables)
     (log "Report tables" report-tables)

     (log "Rnit" rnit)

     (log "Report template" demo-template-table)
     (log "Report" demo-report-table)
     (log "YY" demo-report-parts demo-report-fields)
     (log "ZZ" parts-not-in-fields (count parts-not-in-fields))
     (log "WW" fields-not-in-parts))))

(defn get-report-info [report-guid]
  "Get info representing a single report from a guid passing on to a callback.
   This method could be split into several"
  (go
    (let
      [
       report (<! (<api (str "Report?reportGuid=" report-guid)))
       report-table (get report "ReportTable")
       template-guid (get report-table "TemplateGuid")

       report-template (<! (<api (str "ReportTemplate?templateGuid=" template-guid)))
       report-template-table (get report-template "ReportTemplateTable")

       report-role-response (<! (<api (str "Report/Role?reportGuid=" report-guid)))
       report-rols (get report-role-response "ReportRols")

       active-area-guids (get report-template-table "ActiveAreaGuids")

       ;; TODO There may be multiple areas
       objects-response (<! (<api (str "Object?areaGuid=" (first active-area-guids))))
       objects (get objects-response "Objects")
     ]
      {
       :report report-table
       :template report-template-table
       :roles report-rols
       :objects objects
       })))

(defn post-new-report [objectId templateGuid reportName done]
  "create a new service report"
  (<api (str "Report?objectId=" objectId
             "&templateGuid=" templateGuid
             "&reportName=" reportName)
        :method "POST"))

(defn put-part [obj]
  "update service report part using http://app.fmtools.dk/Help/Api/PUT-api-v1-Report-Part"
  (<api "Report/Part"
        :method "PUT"
        :data obj))

(defn put-field [obj]
  (<api "Report/Field"
        :method "PUT"
        :data obj))

#_(deftest report-field-and-part-update-trail
         (log  (<! (get-report-info "ca968ee8-373e-442a-b173-edbbcfeb4b90"
                          #(
                            log "Report cdb9" %
                            (post-new-report
                                235
                                "09ec19cb-a46e-44c0-9edf-74cd60554443"
                                "FooReport"
                                done
                                )
                            )))))

#_(deftest add-files-to-report
  ""
  (async done
         (get-report-info "ca968ee8-373e-442a-b173-edbbcfeb4b90"
                          #(

                            )
                          )
         )
  )

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (if (cljs.test/successful? m)
    (println "Success!")
    (println "FAIL")))

(defn dblog []
	(log "DB" @(subscribe [:db])))

(js/setTimeout #(do (.clear js/console) (run-tests)) 550)

(aset js/window "db" dblog)
(aset js/window "tests" #(run-tests))
(aset js/window "gri"
          #(go (log  (<! (get-report-info "ca968ee8-373e-442a-b173-edbbcfeb4b90")))))
