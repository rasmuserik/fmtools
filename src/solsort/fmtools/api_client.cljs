(ns solsort.fmtools.api-client
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require
   [solsort.fmtools.util :refer [third to-map delta empty-choice <chan-seq <localforage fourth-first]]
   [solsort.fmtools.disk-sync :as disk-sync]
   [solsort.util
    :refer
    [<p <ajax <seq<! js-seq normalize-css load-style! put!close!
     log page-ready render dom->clj next-tick]]
   [clojure.walk :refer [keywordize-keys]]
   [re-frame.core :as re-frame
    :refer [register-sub subscribe register-handler
            dispatch dispatch-sync]]
   [cljs.core.async :as async :refer [>! <! chan put! take! timeout close! pipe]]))

(defonce field-types
  {0   :none
   1   :text-fixed
   2   :text-input
   3   :checkbox
   4   :integer
   5   :decimal-2-digit
   6   :decimal-4-digit
   7   :date
   8   :time
   9   :text-fixed-noframe
   10  :text-input-noframe
   11  :approve-reject
   12  :fetch-from
   13  :remark
   100 :case-no-from-location})
(defonce part-types
  {0 :none
   1 :header
   2 :line
   3 :footer})
(defonce line-types
  {0  :basic
   1  :simple-headline
   2  :vertical-headline
   3  :horizontal-headline
   4  :multi-field-line
   5  :description-line
   10 :template-control})

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
    (let [template (keywordize-keys
                    (<! (<api (str "ReportTemplate?templateGuid="
                                   template-id))))
          template (:ReportTemplateTable template)
          fields (-> template
                     (:ReportTemplateFields )
                     (->>
                      (map #(assoc % :FieldType (field-types (:FieldType %))))
                      (sort-by :DisplayOrer)
                      (group-by :PartGuid)))
          parts (-> template (:ReportTemplateParts))
          parts (map
                 (fn [part]
                   (assoc part :fields
                          (sort-by :DisplayOrder
                                   (get fields (:PartGuid part)))))
                 (sort-by :DisplayOrder parts))
          parts (map #(assoc % :LineType (or (line-types (:LineType %))
                                             (log "invalid-LintType" %))) parts)
          parts (map #(assoc % :PartType (part-types (:PartType %))) parts)]
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
    (let [objects (:Objects
                   (keywordize-keys
                    (<! (<api (str "Object?areaGuid=" (:AreaGuid area))))))]
      (doall
       (for [object objects]
         (let [object (assoc object :AreaName (:Name area))]
           (dispatch-sync [:area-object object])
           ))))
    (log 'load-area (:Name area))))
(defn <load-objects []
  (go (let [areas (keywordize-keys (<! (<api "Area")))]
        (<! (<chan-seq (for [area (:Areas areas)]
                         (<load-area area))))
        (log 'objects-loaded))))

(defn <load-report [report]
  (go
    (let [data (keywordize-keys (<! (<api (str "Report?reportGuid=" (:ReportGuid report)))))
          role (keywordize-keys (<! (<api (str "Report/Role?reportGuid=" (:ReportGuid report)))))]
      (dispatch-sync [:raw-report report data role])
      (log 'report (:ReportName report)))))
(defn <load-reports []
  (go
    (let [reports (keywordize-keys (<! (<api "Report")))]
      (<! (<chan-seq
           (for [report (:ReportTables reports)]
             (<load-report report))))
      (log 'loaded-reports)
      )))
(defn handle-reports [] ; TODO delete?
  (let [raw-reports (:raw-report @(subscribe [:db]))]
    (doall
     (for [[_ raw-report] raw-reports]
       (let [report (:report raw-report)
             report-guid (:ReportGuid report)
             data (:ReportTable (:data raw-report))
             role (:role raw-report)]
         (do
           (log 'report report-guid data)
           (doall
            (for [entry (:ReportFields report)]
              (dispatch-sync [:db report-guid (:FieldGuid entry) ()])))))))))
#_(handle-reports)

(defn <fetch []
  (go
    (dispatch-sync [:db :loading true])
    (<! (<chan-seq [(<load-reports)
                 (<load-templates)
                 (<load-objects)
                 #_(go (let [user (keywordize-keys (<! (<api "User")))] (dispatch [:user user])))]))
    (<! (disk-sync/<save-form))
    (dispatch-sync [:db :loading false])
    ))


#_(fetch)
(defonce loader (<fetch))
