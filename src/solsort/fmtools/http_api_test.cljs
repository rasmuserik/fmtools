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
      [log next-tick]]
     [solsort.fmtools.util :refer
      [str->timestamp timestamp->isostring]]
     [re-frame.core :as re-frame
      :refer [register-sub subscribe register-handler
              dispatch dispatch-sync]]
     [cljs.test :refer-macros  [deftest is testing run-tests async]]))

(defn without-matching-guid [parts fields]
  "get PartGuids of objects in parts that do not have a corresponding PArtGuid in fields"
  (loop [ps parts res []]
        (if (empty? ps)
          res
          (let [part (first ps)
                part-guid (get part "PartGuid")
                matching (first (filter #(= part-guid (get % "PartGuid")) fields))]
            (if matching
              (recur (rest ps) res)
              (recur (rest ps) (conj res part-guid)))))))

(deftest get-demo-report-and-objects
  (go
   ;; Requests related to the DEMO report to serve as documentation of the API
   (let [
         ;; Get a shallow description of all reports
         reports-response (<! (<api "Report"))
         tables (ReportTables reports-response)
         demo (filter #(re-matches #"DEMO.*" (ReportName %)) tables)
         demo-report-info (first demo)
         report-guid (ReportGuid demo-report-info)
         template-guid (TemplateGuid demo-report-info)

         ;; Get the full DEMO report including fields, parts and files
         demo-report-response (<! (<api (str "Report?reportGuid=" report-guid)))
         demo-report-table (ReportTable demo-report-response)
         demo-report-fields (ReportFields demo-report-table)
         demo-report-files (get demo-report-table "ReportFiles")
         demo-report-parts (get demo-report-table "ReportParts")

         ;; XXX It seems that there are parts not referenced from any fields.  What are they then used for?
         parts-not-in-fields (without-matching-guid demo-report-parts demo-report-fields)
         fields-not-in-parts (without-matching-guid demo-report-fields demo-report-parts)

         ;; Get the full report template for this report
         report-template-response (<! (<api (str "ReportTemplate?templateGuid=" template-guid)))
         report-template-table (ReportTemplateTable report-template-response)
         active-area-guid (first (get report-template-table "ActiveAreaGuids"))

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

     (log "YY" demo-report-parts demo-report-fields)
     (log "ZZ" parts-not-in-fields (count parts-not-in-fields))
     (log "WW" fields-not-in-parts)
     )))

(deftest demo-files
  "Find the files"
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
