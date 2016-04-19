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
        [solsort.util
         :refer
         [<ajax <seq<! js-seq normalize-css load-style! put!close!
          parse-json-or-nil log page-ready render dom->clj]]
        [reagent.core :as reagent :refer []]
        [re-frame.core :as re-frame
         :refer [register-sub subscribe register-handler dispatch dispatch-sync]]
        [clojure.string :as string :refer [replace split blank?]]
        [cljs.core.async :refer [>! <! chan put! take! timeout close! pipe]]))

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
       { :display :none }
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
          [:input {:type "file" :capture "camera" :accept "image/*" :id id}]
          ])))

### Main App entry point

    (defn app []
      [:div.ui.container
       [:h1 "FM-Tools"]
       [:hr]
       [:div.right [camera-button]]
       [:hr]])

### Execute and events

    (render [app])

