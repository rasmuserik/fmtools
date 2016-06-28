(ns solsort.fmtools.ui
    (:require-macros
     [cljs.core.async.macros :refer [go go-loop alt!]]
     [reagent.ratom :as ratom :refer  [reaction]])
    (:require
     [devtools.core :as devtools]
     [cljs.pprint]
     [cljsjs.localforage]
     [cognitect.transit :as transit]
     [solsort.misc :refer [<blob-url]]
     [solsort.util
      :refer
      [<p <ajax <seq<! js-seq normalize-css load-style! put!close!
       parse-json-or-nil log page-ready render dom->clj next-tick]]
     [reagent.core :as reagent :refer []]
     [cljs.reader :refer [read-string]]
     [clojure.data :refer [diff]]
     [clojure.walk :refer [keywordize-keys]]
     [re-frame.core :as re-frame
      :refer [register-sub subscribe register-handler
              dispatch dispatch-sync]]
     [clojure.string :as string :refer [replace split blank?]]
     [cljs.core.async :as async :refer [>! <! chan put! take! timeout close! pipe]]))

