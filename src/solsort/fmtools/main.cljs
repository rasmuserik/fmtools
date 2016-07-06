(ns solsort.fmtools.main
  (:require-macros
   [reagent.ratom :as ratom]
   [cljs.core.async.macros :refer [go]])
  (:require
   [cljs.core.async :as async :refer [<!]]
   [solsort.fmtools.util]
   [solsort.fmtools.db :as db]
   [solsort.fmtools.ui]
   [solsort.fmtools.api-client :as api]
   [solsort.fmtools.disk-sync :as disk]))

(defonce load-data
  (go
    (<! (disk/<restore-form))
    (<! (api/<fetch))
    (<! (disk/<save-form))
    (disk/watch-changes! :data) 
    ))

