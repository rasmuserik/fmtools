(ns solsort.fmtools.db
  (:require-macros
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
   [solsort.appdb :as appdb]
   [solsort.util :refer [log next-tick]]
   [reagent.core :as reagent :refer []]))

(defonce api-db (atom nil))

(def db! appdb/db!)
(def db-async! appdb/db-async!)
(def db appdb/db)
