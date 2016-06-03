[![Stories in Ready](https://badge.waffle.io/solsort/fmtools.png?label=ready&title=Ready)](https://waffle.io/solsort/fmtools)
[![Build Status](https://travis-ci.org/solsort/fmtools.svg?branch=master)](https://travis-ci.org/solsort/fmtools)

# FM-Tools

Formålet er at lave en simpel app hvor det er let at udfylde rapporter fra FM-tools.

Krav til app'en:

- muligt at udfylde rapporterne, ud fra rapportskabelon bestående af linjer med felter
- understøtte dynamiske rapportskabeloner, hvor afsnit(linjer) af rapporten bliver gentaget for hver enhed på de forskellige niveauer. (eksempelvie projekt/tavle/anlæg/komponent)
- muligt at navigere mellem enheder på forskellige niveauer, og finde rapport for pågældende ehned
- forskellige former for felter, ie.: overskrifter/labels, tekstformulare, checkbokse, tal, dato, etc.
- muligt at vedhæfte/se billeder for hver linje i formularen
- formater: håndholdt mobil, samt tablet
- skal kunne funger/udfyldes offline, udfyldte formularer synkroniseres næste gang at der er internetforbindelse
- skal fungere på nyere Android og iOS, - enten som webapp, eller som hybrid app hvis ikke al nødvendig funktionalitet er tilgængelig via webbrowseren.

# Task

## done 0.0.1

- ui
  - initial camera button (only currently only working on android)
  - simple buggy rendition of templates, test that table-format also works on mobile (mostly)
  - checkbox component that writes to application database
  - generic select widget
  - choose current template (should be report later)
  - more responsive ui, instead of mobile-portrait oriented
- data
  - basic communication with api - load data

## Next

- Proxy api on demo-deploy-server
- refactor/update code
- simplify data from api
- widgets
- make it work on iOS (currently probably CORS-issue, maybe try out proxy through same domain as deploy)
- proper horizontal labels
- better sync'ing of data
- separate ids for double-checkboxes

# Literate source code


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

# Util

    (when js/window.applicationCache
      (aset js/window.applicationCache "onupdateready" #(js/location.reload)))
# Definitions

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

# Application database
## UI

    (register-sub
      :ui (fn  [db [_ id]]  (reaction (get-in @db [:ui id]))) )
    (register-handler
      :ui (fn  [db  [_ id data]] (assoc-in db [:ui id] data)))

## Templates
    (register-sub
      :templates (fn  [db]  (reaction (keys (get @db :templates {})))))
    (register-sub
      :template (fn  [db [_ id]]  (reaction (get-in @db [:templates id] {}))))
    (register-handler
      :template
      (fn  [db  [_ id template]]
        (dispatch [:sync-to-disk])
        (assoc-in db [:templates id] template)))

## Objects
    (register-handler
      :area-object
      (fn  [db  [_ id object]]
        (assoc-in db [:objects id] object)))
    (register-handler
      :area-object-graph
      (fn  [db  [_ from to]]
        (assoc-in db [:object-graph from to] true)))

## Simple disk-sync
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

# Styling

    (declare app)
    (defonce unit (atom 40))
    (defn style []
      (reset! unit (js/Math.floor (* 0.95 (/ 1 12) (js/Math.min 800 js/window.innerWidth))))
      (log 'style @unit)
      (let [unit @unit]
        (load-style!
          {:#main
           {:text-align :center}
           :.line
           {:min-height 44}
           :.main-form
           {:display :inline-block
            :text-align :left
            :width (* unit 12)}
           :.camera-input
           {:display :inline-block
            :position :absolute
            :right 0 }
           :.fmfield
           {:clear :right }
           :.checkbox
           {; :display :inline-block
            ;:border "2px solid black"
            ;:border-radius (* unit .25)
            ;:font-size unit
            ;:line-height (* unit .8)
            ;:margin (* unit .25)
            :width 44
            :max-width "95%"
            :height 44

            }
           :.multifield
           {:border-bottom "0.5px solid #ccc"}
           ".camera-input img"
           {:height 40
            :width 40
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

# Generic Components
## select
    (defn select [id options]
      (into [:select
             {:onChange
              #(dispatch [:ui id (.-value (.-target %1))])}]
            (for [[k v] options]
              [:option {:key v :value v} k])))

## checkbox

    (defn checkbox [id]
      (let [value @(subscribe [:ui id])]
        [:img.checkbox
         {:on-click #(dispatch [:ui id (not value)])
          :src (if value "assets/check.png" "assets/uncheck.png")}]))

# App layout
## Camera button

    (defn camera-button []
      (let [id (str "camera" (js/Math.random))]
        (fn []
          [:div.camera-input
           [:label {:for id}
            [:img.camera-button {:src "assets/camera.png"}]]
           ; TODO apparently :camera might not be a supported property in react
           [:input {:type "file" :capture "camera" :accept "image/*" :id id :style {:display :none}}]
           ])))

## Template rendition
    (defn field [field cols]
      (let [id (:FieldGuid field) ]
        [:span.fmfield {:key id
                        :style
                        {:width (* 11 @unit (/ (:Columns field) cols))
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

## main
    (defn form []
      [:div.main-form
       [:div.ui.container
        [:div.ui.form
         [:div.field
          [:label "Skabelon"]
          [select :current-template

           (for [template-id  @(subscribe [:templates])]
             [(str (:Name @(subscribe [:template template-id])) " / "
                   (:Description @(subscribe [:template template-id])))
              template-id])]]]]
       [:hr]
       [render-template @(subscribe [:ui :current-template])]])

    (defn app []
      [:div

       [:h1 "FM-Tools"]
       [:hr]
       [form]
       ])

# Loading-Data
## <api

    (defn <api [endpoint]
      (<ajax (str "https://"
                  "fmtools.solsort.com/api/v1/"
                  ;"app.fmtools.dk/api/v1/"
                  ;(js/location.hash.slice 1)
                  ;"@fmproxy.solsort.com/api/v1/"
                  endpoint)
             :credentials true))

## Templates
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

## Objects
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
## Report


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

## fetch

    (defn fetch []
      (load-templates)
      ;(go (let [user (keywordize-keys (<! (<api "User")))] (dispatch [:user user])))
      ; (load-objects)
      ; (load-reports)
      )

    (defonce loader
      (fetch))
