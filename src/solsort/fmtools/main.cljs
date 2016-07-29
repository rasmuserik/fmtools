(ns solsort.fmtools.main
  (:require-macros
   [reagent.ratom :as ratom]
   [cljs.core.async.macros :refer [go]])
  (:require
   [cljs.core.async :as async :refer [<!]]
   [solsort.util :refer [<p]]
   [solsort.fmtools.util]
   [solsort.fmtools.db :as db]
   [solsort.fmtools.ui]
   [solsort.fmtools.data-index]
   [solsort.fmtools.changes]
   [solsort.fmtools.api-client :as api]
   [solsort.fmtools.disk-sync :as disk]))

(go
  (when (not= -1 (.indexOf js/location.hash "reset"))
    (<! (<p (js/localforage.clear))))
  (defonce restore-data
    (<! (disk/<restore-form)))
  (when (= -1 (.indexOf js/location.hash "noload"))
    (<! (api/<fetch)))
  (defonce watch
    (do
      (disk/watch-changes! :state)
      (disk/watch-changes! :data)
      nil)))
