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

# General notes
## Næste trin

Første trin er at vi laver et udkast til hvordan datamodel / API kunne se ud.

Hvis jeg skal gå i gang med dette, har jeg brug for adgang til web-applikationen, så jeg kan se præcist hvilke felter etc. der er.

Nå vi er enige om et første udkast på datamodellen kan Kasper gå i gang med APIet, og jeg kan gå i gang med App'en parallelt.

# API and data model

- Skabeloner består af linjer med felter
- Enheder ligger i en træstruktur med et antal niveauer
- Rapporter er skabelon der er ved at blive udfyldt, og representeret ved en event-log over indtastninger

### API

Notes about actual api:

- Login brugernavn+password, using basic auth
- `/help` gives overview of the api
- first step is to get an endpoint for skabeloner

- Rapporter/skabeloner
    - timestamp - last-change for rapportskabelon
    - download af rapportskabelon (liste af linjer, hvor hver linje har info, samt en liste af felter)
    - liste af raporter
    - liste af niveauer / 
- Data for udfyldelse - eventlog bestående af liste af (tidsstempel, sammensat felt-id, værdi). Sammensat felt-id består af reference til felt i skabelonen, rapportid, samt berørt enhed (projekt/tavel/anlæg/komponent). Timestamp gør at det er til at merge. 
    - send event
    - hent events modtaget efter et givent tidsstempel
- Fotos
    - upload: linjeid, billeddata ->
    - liste: linjeid -> liste over billeder vedhæftet den pågældende linje, m. url'er

# Literate source code

Currently just dummy to get project started

    (ns solsort.fmtools.main
      (:require-macros
        [cljs.core.async.macros :refer [go go-loop alt!]]
        [reagent.ratom :as ratom :refer  [reaction]])
      (:require
        [cljs.pprint]
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

    (register-sub
      :templates (fn  [db]  (reaction (keys (get @db :templates {})))))
    (register-sub
      :template (fn  [db [_ id]]  (reaction (get-in @db [:templates id] {}))))
    (register-handler
      :template
      (fn  [db  [_ id template]] (assoc-in db [:templates id] template)))

## Definitions

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
      {0  :simple-headline
       1  :vertical-headline
       2  :horizontal-headline
       4  :multi-field-line
       5  :description-line
       10 :template-control})
## Application database

## Synchronization with API

## Styling

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
       ".fmfield"
       {;:display :inline-block
        ;:margin "1em"
        ;:padding "1em"
        ; :border "1px solid black"

        }
       ".line"
       {:margin "1em"
        :padding "1em"
        :border "1px solid black" }
       ".checkbox"
       {:display :inline-block
        :border "2px solid black"
        :border-radius 8
        :font-size 32
        :line-height 28
        :margin 8
        :width "32px"
        :height "32px"
        }
       }
      "basic-style")


## Components

### Camera button

    (defn camera-button []
      (let [id (str "camera" (js/Math.random))]
        (fn []
          [:div.camera-input
           [:label {:for id}
            [:img.camera-button {:src "assets/camera.png"}]]
           ; TODO apparently :camera might not be a supported property in react
           [:input {:type "file" :capture "camera" :accept "image/*" :id id}]
           ])))

### Main App entry point

    (defn checkbox [id]
      [:span.checkbox
       ;"✓"
       (if (< 0.9 (js/Math.random)) "\u00a0" "✔")
       ]
      )
    (defn field [field]
      (let [id (:FieldGuid field)]
        [:span.fmfield {:key id
                        :on-click (fn [] (js/alert (str field)) false)}
         (case (:FieldType field)
           :text-fixed [:span.text-fixed-frame (:FieldValue field)]
           :text-input [:input {:type :text :name (:FieldGuid field)}]
           :decimal-2-digit
           [:div.ui.input
            [:input {:type :text :size 2 :max-length 2 :name (:FieldGuid field)}]]
           :checkbox
           (if (:DoubleField field)
             [:span [checkbox (:FieldGuid field)] " / " [checkbox (:FieldGuid field)] ]
             [checkbox (:FieldGuid field)])
           :text-fixed-noframe [:span.text-fixed-noframe (:FieldValue field)]
           [:strong "unhandled field:"
            (str (:FieldType field)) " "  (:FieldValue field)])
         ; [:div {:style {:font-size 9 :line-height "8px"}} (str field)]

         ]))

    (defn line [line]
      (let [id (:PartGuid line)]
        [:div.line
         {:key id
          :on-click #(js/alert (str (dissoc line :fields)))}
         (case (:LineType line)
           :simple-headline [:h3 "_ " (:TaskDescription line)]
           ;:vertical-headline [:h3.vertical (:TaskDescription line)]
           :vertical-headline (into [:div [:h3.vertical ". "
                                           (:TaskDescription line)]]
                                    (map field (:fields line)))
           :horizontal-headline (into [:div [:h3.vertical ", "
                                             (:TaskDescription line)]]
                                      (map field (:fields line)))
           :multi-field-line (into [:div "* " (:TaskDescription line) [:br]]
                                   (map field (:fields line)))
           [:strong {:key id} "unhandled line " (str (:LineType line)) " "
            (:FieldValue field)])
         ]))

    (defn render-template [id]
      (let [template @(subscribe [:template id])]
        ;(log (with-out-str (cljs.pprint/pprint template)))
        (merge
          [:div.ui.form
           [:h1 (:Description template)]]
          (map line (:rows template))
          [:pre
           (js/JSON.stringify (clj->js template) nil 2)]
          ;[:pre (str (cljs.pprint/pprint template))]

          )))

    (defn form []
      (let [templates @(subscribe [:templates])]
        #_(into [:div]
                (for [template-id templates]
                  [render-template template-id]))
        [render-template (nth templates 3)]

        ))

    (defn app []
      [:div.ui.container
       [:h1 "FM-Tools"]
       [:hr]
       [form]
       ;[:div.right [camera-button]]
       [:hr]])

### Execute and events

    (render [app])

    (defn <api [endpoint]
      (<ajax (str "https://"
                  (js/location.hash.slice 1)
                  "@fmproxy.solsort.com/api/v1/"
                  ;"@fmproxy.solsort.com/api/v1/"
                  endpoint)
             :credentials true))

    (defn load-template [template-id]
      (go
        (let [template (keywordize-keys
                         (<! (<api (str "ReportTemplate?templateGuid="
                                        template-id))))
              template (:ReportTemplateTable template)
              fields (-> template
                         (:ReportTemplateFields )
                         (->>
                           (map #(assoc % :FieldType (field-types (:FieldType %))))
                           (sort-by :DisplayOrer)
                           (group-by :PartGuid)))
              parts (-> template
                        (:ReportTemplateParts))
              parts (map
                      (fn [part]
                        (assoc part :fields
                               (sort-by :DisplayOrder
                                        (get fields (:PartGuid part)))))
                      (sort-by :DisplayOrder parts))
              parts (map #(assoc % :LineType (line-types (:LineType %))) parts)
              parts (map #(assoc % :PartType (part-types (:PartType %))) parts)
              ]
          (dispatch [:template template-id (assoc template :rows parts)]))))

    (defonce fetch
      (go
        (let [templates (<! (<api "ReportTemplate"))
              template-id (-> templates
                              (get "ReportTemplateTables")
                              (nth 0)
                              (get "TemplateGuid"))]
          (doall (for [template (get templates "ReportTemplateTables")]
                   (load-template (get template "TemplateGuid")))))))
