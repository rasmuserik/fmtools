(ns solsort.fmtools.db
  (:require-macros
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
   [solsort.toolbox.appdb :as appdb]
   [solsort.util :refer [log next-tick]]
   [reagent.core :as reagent :refer []]))

(defonce api-db (atom nil))
(def db! appdb/db!)
(def db-async! appdb/db-async!)
(def db appdb/db)

(defonce server-name (atom nil))

(defn set-server [server]
  (reset! server-name server)
  (db-async! [:obj :settings]
             (into (db [:obj :settings] {:id :settings
                                         :local :true})
                   {:server server})))
(defn update-server-settings []
  (set-server (js/prompt "Indtast servernavn, i.e.: \"app.fmtools.dk\"")))
(defn server-host []
  (while (not @server-name) (update-server-settings))
  @server-name)
