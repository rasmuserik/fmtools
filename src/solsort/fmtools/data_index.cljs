(ns solsort.fmtools.data-index
  (:require
   [solsort.util :refer [log]]
  [solsort.fmtools.db :refer [db db! obj]]))

;; experiments to make index of actual data entries, - turns out that we do not have object id 
(def entry-type #{:part-entry :field-entry})
(defn warn [& args]
  ;;(apply log args)
  nil)
(defn update-entry-index! []
  (db! [:entries]
  (reduce
   (fn [acc entry]
     (let [id (:id entry)
           template-id (or
                        (get entry "TemplateFieldGuid")
                        (get entry "TemplatePartGuid"))
           part-id (get entry "PartGuid")
           part (obj (get entry "PartGuid"))
           ;; Note: It seems like we do not get actual objIds from server
           obj-id (get part "ObjectId" :missing)
           report-id (get part "ReportGuid")
           path [report-id obj-id template-id]]
       (when-not part
         (warn "part missing" entry))
       (when (= :missing obj-id)
         (warn "no ObjId" entry part))
       (when (get acc path)
         (warn "warning, duplicate data entry path" path (:id entry)))
       (assoc acc path (:id entry))
       ))
   {}
   (filter #(entry-type (:type %))(vals (db [:obj])))))
  (log 'updated-entry-index))
(update-entry-index!)
