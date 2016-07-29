(ns solsort.fmtools.data-index
  (:require
   [solsort.util :refer [log]]
  [solsort.fmtools.db :refer [db db! db-sync! obj]]))

;; experiments to make index of actual data entries, - turns out that we do not have object id 
#_(do
  (log
   (distinct
    (map :type @(db :obj))))

(def entry-type #{:part-entry :field-entry})
(distinct (map :type (vals @(db :obj)) ))
(log (filter #(= "466351d9-db30-43f8-9bfb-9e90c81bd5e6" (get % "TemplatePartGuid")) (vals @(db :obj))))
(get (obj "466351d9-db30-43f8-9bfb-9e90c81bd5e6") "PartGuid")

(let [entries (take 10000 (filter #(entry-type (:type %))(vals @(db :obj))))]
  (log (count entries))
  (log (group-by identity (map #(get % "ObjectId" :missing) entries)))
  (log (group-by identity (map #(get (obj (get % "PartGuid")) "ObjectId" :missing) entries)))
  (log entries)
  #_(reduce
   (fn [acc entry]
     (log 'here)
     (let [id (:id entry)
           partId (get entry "PartGuid")
           part (obj (get entry "PartGuid"))
           objId (get (obj (get entry "PartGuid")) "ObjectId" :missing)
           ]
       (log part objId)
       
     ))
   {} entries)
  )

(log 'here)
)
