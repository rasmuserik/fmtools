(ns solsort.fmtools.main
  (:require-macros
   [reagent.ratom :as ratom]
   [cljs.core.async.macros :refer [go]])
  (:require
   [cljs.core.async :as async :refer [<!]]
   [solsort.util :refer [<p log]]
   [solsort.fmtools.util]
   [solsort.fmtools.db :as db]
   [solsort.fmtools.data-index :refer [update-entry-index!]]
   [solsort.fmtools.ui]
   [solsort.fmtools.data-index]
   [solsort.fmtools.changes :as changes]
   [solsort.fmtools.api-client :as api]
   [solsort.fmtools.disk-sync :as disk]))

(go
  (when (not= -1 (.indexOf js/location.hash "reset"))
    (<! (<p (.clear disk/localforage-db))))
  (db/db! [:loading] true)
  (defonce restore-data
    (<! (disk/<restore)))
  (db/db! [:loading] false)
  (when (= -1 (.indexOf js/location.hash "noload"))
    (<! (api/<fetch)))
  (when (empty? (db/db [:entries]))
    (update-entry-index!))
  (defonce initialisation
    (do
      (changes/init)
      nil)))
