;; # Literate source code
;;
;; Currently just dummy to get project started
;;
(ns solsort.fmtools.main
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop alt!]]
    [reagent.ratom :as ratom :refer  [reaction]])
  (:require
    [solsort.util
     :refer
     [<ajax <seq<! js-seq normalize-css load-style! put!close!
      parse-json-or-nil log page-ready render dom->clj]]
    [reagent.core :as reagent :refer []]
    [clojure.walk :refer [keywordize-keys]]
    [re-frame.core :as re-frame
     :refer [register-sub subscribe register-handler dispatch dispatch-sync]]
    [clojure.string :as string :refer [replace split blank?]]
    [cljs.core.async :refer [>! <! chan put! take! timeout close! pipe]]))

(register-sub :template (fn  [db  [_ id]]  (reaction (get @db :template []))))
(register-handler :template (fn  [db  [_ template]] (assoc db :template template)))


;; ## Application database
;;
;; ## Synchronization with API
;;
;; ## Styling
;;
(load-style!
  {:.float-right
   {:float :right}
   :.right
   {:text-align :right}
   ".camera-input img"
   {:height 44
    :width 44
    :padding 4
    :border "2px solid black"
    :border-radius 6
    }
   ".camera-input input"
   {}
   }
  "basic-style")


;; ## Components
;;
;; ### Camera button
;;
(defn camera-button []
  (let [id (str "camera" (js/Math.random))]
    (fn []
      [:div.camera-input
       [:label {:for id}
        [:img.camera-button {:src "assets/camera.png"}]]
       ; TODO apparently :camera might not be a supported property in react
       [:input {:type "file" :capture "camera" :accept "image/*" :id id}]
       ])))
;;
;; ### Main App entry point
;;
(defn field [field]
  [:span.field (:FieldValue field) " "])
(defn line [line]
  [:p.line
           (into [:div] (map field (:fields line)))
           [:div {:style {:font-size 8 :line-height "8px"}} (str line)]]

  )
(defn form []
  (let [template @(subscribe [:template])]
    (into
      [:div]
      (map line template)))
  )
(defn app []
  [:div.ui.container
   [:h1 "FM-Tools"]
   [:hr]
   [form]
   [:div.right [camera-button]]
   [:hr]])

;; ### Execute and events

(render [app])

(defn <api [endpoint]
  (<ajax (str "https://"
              (js/location.hash.slice 1)
              "@fmproxy.solsort.com/api/v1/"
              endpoint)
         :credentials true
         ))

(go
  (let [templates (<! (<api "ReportTemplate"))
        templateId (-> templates
                       (get "ReportTemplateTables")
                       (nth 4)
                       (get "TemplateGuid")
                       )
        template-table (:ReportTemplateTable
                   (keywordize-keys
                     (<! (<api (str "ReportTemplate?templateGuid=" templateId)))))
        fields (-> template-table
                   (:ReportTemplateFields )
                   (->>
                     (sort-by :DisplayOrer)
                     (group-by :PartGuid)))
        parts (-> template-table
                  (:ReportTemplateParts))
        parts (map
                (fn [part]
                  (assoc part :fields
                         (get fields (:PartGuid part))))
                (sort-by :DisplayOrder parts))

        ]
    (log templates)
    (log fields)
    (log parts)
    (dispatch [:template parts])))
