;; # Task
;;
;; ## Done
;;
;; - basic communication with api
;; - simple buggy rendition of templates, test that table-format also works on mobile (mostly)
;;
;; ## Next
;;
;; - refactor/update code
;; - simplify data from api
;; - widgets
;; - make it work on iOS (currently probably CORS-issue, maybe try out proxy through same domain as deploy)
;; - proper horizontal labels
;;
;; # Literate source code
;;
;;
(ns solsort.fmtools.main
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop alt!]]
    [reagent.ratom :as ratom :refer  [reaction]])
  (:require
    [cljs.pprint]
    [cognitect.transit :as transit]
    [solsort.util
     :refer
     [<ajax <seq<! js-seq normalize-css load-style! put!close!
      parse-json-or-nil log page-ready render dom->clj]]
    [reagent.core :as reagent :refer []]
    [clojure.walk :refer [keywordize-keys]]
    [re-frame.core :as re-frame
     :refer [register-sub subscribe register-handler
             dispatch dispatch-sync]]
    [clojure.string :as string :refer [replace split blank?]]
    [cljs.core.async :refer [>! <! chan put! take! timeout close! pipe]]))

;; # Definitions
;;
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

;; # Application database
;; ## Templates
(register-sub
  :templates (fn  [db]  (reaction (keys (get @db :templates {})))))
(register-sub
  :template (fn  [db [_ id]]  (reaction (get-in @db [:templates id] {}))))
(register-handler
  :template
  (fn  [db  [_ id template]]
    (dispatch [:sync-to-disk])
    (assoc-in db [:templates id] template)))

;; ## Objects
(register-handler
  :area-object
  (fn  [db  [_ id object]]
    (assoc-in db [:objects id] object)))
(register-handler
  :area-object-graph
  (fn  [db  [_ from to]]
    (assoc-in db [:object-graph from to] true)))

;; ## Simple disk-sync
(defn clj->json [s] (transit/write (transit/writer :json) s))
(defn json->clj [s] (transit/read (transit/reader :json) s))

(register-handler
  :sync-to-disk
  (fn  [db]
    ; currently just a hack, needs reimplementation on localforage
    ; only syncing part of structure that is changed
    (js/localStorage.setItem "db" (js/JSON.stringify (clj->json db)))
    db))

(register-handler
  :restore-from-disk
  (fn  [db]
    (json->clj (js/JSON.parse (js/localStorage.getItem "db")))))
(dispatch [:restore-from-disk])

;; # Components
;; ## Styling

(declare app)
(defonce unit (atom 10))
(defn style []
  (reset! unit (js/Math.floor (* 0.90 (/ 1 12) js/window.innerWidth)))
  (log 'style @unit)
  (let [unit @unit]
    (load-style!
      {:.camera-input
       {:display :inline-block
        :position :absolute
        :right 0
        }
       :.fmfield
       {:clear :right }
       :.checkbox
       {; :display :inline-block
        ;:border "2px solid black"
        ;:border-radius (* unit .25)
        ;:font-size unit
        ;:line-height (* unit .8)
        ;:margin (* unit .25)
        :width unit
        :max-width "95%"
        :height unit
        
        }
       :.multifield
       {:border-bottom "0.5px solid #ccc"}
       ".camera-input img"
       {:height unit
        :width unit
        :padding 4
        :border "2px solid black"
        :border-radius 6
        :opacity "0.5"
        }
       :.fields
       {:text-align :center }
       }
      "check-style"))
  (render [app]))
(aset js/window "onresize" style)
(js/setTimeout style 0)

;; ## Camera button

(defn camera-button []
  (let [id (str "camera" (js/Math.random))]
    (fn []
      [:div.camera-input
       [:label {:for id}
        [:img.camera-button {:src "assets/camera.png"}]]
       ; TODO apparently :camera might not be a supported property in react
       [:input {:type "file" :capture "camera" :accept "image/*" :id id :style {:display :none}}]
       ])))

;; Item components

(defn checkbox [id]
  [:img.checkbox
   {:src (if (< 0.7 (js/Math.random)) "assets/uncheck.png" "assets/check.png")}])

(defn field [field cols]
  (let [id (:FieldGuid field) ]
    [:span.fmfield {:key id
                    :style
                    {:width (* 12 @unit (/ (:Columns field) cols))
                     :vertical-align :top
                     :display :inline-block
                     ;:border-left "1px solid black"
                     ;:border-right "1px solid black"
                     :text-align :center}
                    :on-click (fn [] (log field) false)}
     (case (:FieldType field)
       :fetch-from "Komponent-id"
       :approve-reject
       (if (:DoubleField field)
         [:span
          [checkbox (:FieldGuid field)] " / "
          [checkbox (:FieldGuid field)] " \u00a0 "]
         [checkbox (:FieldGuid field)])
       :text-fixed [:span.text-fixed-frame.outer-vertical
                    [:span.inner-vertical (:FieldValue field)]]
       :time [:input {:type :text :name (:FieldGuid field)}]
       :remark [:input {:type :text :name (:FieldGuid field)}]
       :text-input-noframe [:input {:type :text :name (:FieldGuid field)}]
       :text-input [:input {:type :text :name (:FieldGuid field)}]
       :decimal-2-digit
       [:div.ui.input
        [:input {:type :text :size 2 :max-length 2 :name (:FieldGuid field)}]]
       :checkbox
       (if (:DoubleField field)
         [:span
          [checkbox (:FieldGuid field)] " / "
          [checkbox (:FieldGuid field)] " \u00a0 "]
         [checkbox (:FieldGuid field)])
       :text-fixed-noframe [:span.text-fixed-noframe (:FieldValue field)]
       [:strong "unhandled field:"
        (str (:FieldType field)) " "  (:FieldValue field)])
     ; [:div {:style {:font-size 9 :line-height "8px"}} (str field)]

     ]))

(defn line [line]
  (let [id (:PartGuid line)
        cols (apply + (map :Columns (:fields line)))
        desc (:TaskDescription line)
        fields (into
                 [:div.fields]
                 (map #(field % cols)  (:fields line)))]
    [:div.line
     {:style
      {:padding-top 10
       }
      :key id
      :on-click #(log (dissoc line :fields))}
     (case (:LineType line)
       :basic [:h3 "" (:TaskDescription line)]
       :simple-headline [:h3 (:TaskDescription line)]
       ;:vertical-headline [:h3.vertical (:TaskDescription line)]
       :vertical-headline [:div [:h3 desc] fields]
       :horizontal-headline [:div [:h3 desc ] fields]
       :multi-field-line [:div.multifield desc [camera-button id ]
                          fields ]
       :description-line [:div desc [:input {:type :text}]]
       [:span {:key id} "unhandled line " (str (:LineType line)) " "
        (str (dissoc line :fields))])
     ]))

(defn render-template [id]
  (let [template @(subscribe [:template id])]
    ;(log (with-out-str (cljs.pprint/pprint template)))
    (merge
      [:div.ui.form
       [:h1 (:Description template)]]
      (map line (:rows template))
      ;[:pre (js/JSON.stringify (clj->js template) nil 2)]
      )))

(defn form []
  (let [templates @(subscribe [:templates])]
    (into [:div]
            (for [template-id templates]
              [render-template template-id]))
    ;[render-template (nth templates 3)]

    ))

(defn app []
  [:div
   [:h1 "FM-Tools"]
   [:hr]
   [form]
   ])

;; ## Execute and events

(defn <api [endpoint]
  (<ajax (str "https://"
              (js/location.hash.slice 1)
              "@fmproxy.solsort.com/api/v1/"
              ;"@fmproxy.solsort.com/api/v1/"
              endpoint)
         :credentials true))

;; # Loading-Data
;; ## Templates
(defn load-template [template-id]
  (go
    (let [template (keywordize-keys
                     (<! (<api (str "ReportTemplate?templateGuid="
                                    template-id))))
          template (:ReportTemplateTable template)
          ; TODO: (group-by :ControlGuid (api/v1/ReportTemplate/Control :ReportControls)) into :template-control lines
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
      (dispatch [:template template-id (assoc template :rows parts)]))))

(defn load-templates []
  (go
    (let [templates (<! (<api "ReportTemplate"))
          template-id (-> templates
                          (get "ReportTemplateTables")
                          (nth 0)
                          (get "TemplateGuid"))]
      (doall (for [template (get templates "ReportTemplateTables")]
               (load-template (get template "TemplateGuid")))))))

;; ## Objects
(defn load-area [area]
  (go
    (let [objects (:Objects (keywordize-keys
                              (<! (<api (str "Object?areaGuid=" (:AreaGuid area))))))]
      (doall
        (for [object objects]
          (let [object (assoc object :AreaName (:Name area))]
            (dispatch [:area-object (:ObjectId object) object])
            (dispatch [:area-object-graph (:ParentId object) (:ObjectId object)])
            ))))))

(defn load-objects []
  (go (let [areas (keywordize-keys (<! (<api "Area")))]
        (log 'areas (:Areas areas))
        (doall (for [area (:Areas areas)]
          (load-area area)
          )))))
;; ## Report


(defn load-report [report]
  (go
    (let [data (keywordize-keys (<! (<api (str "Report?reportGuid=" (:ReportGuid report)))))
          role (keywordize-keys (<! (<api (str "Report/Role?reportGuid=" (:ReportGuid report)))))]
      (log report data role))))
(defn load-reports []
  (go
    (let [reports (keywordize-keys (<! (<api "Report")))]
      (doall
        (for [report (:ReportTables reports)]
          (load-report report))))))

;; ## User

(defn fetch []
  ;(load-templates)
  ;(go (let [user (keywordize-keys (<! (<api "User")))] (dispatch [:user user])))
 ; (load-objects)
  (load-reports)
  ; TODO: reports
  )


(fetch)
