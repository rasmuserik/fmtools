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

# Næste trin

Første trin er at vi laver et udkast til hvordan datamodel / API kunne se ud.

Hvis jeg skal gå i gang med dette, har jeg brug for adgang til web-applikationen, så jeg kan se præcist hvilke felter etc. der er.

Nå vi er enige om et første udkast på datamodellen kan Kasper gå i gang med APIet, og jeg kan gå i gang med App'en parallelt.

# Datamodel / API - udkast in progress

- Skabeloner består af linjer med felter
- Enheder ligger i en træstruktur med et antal niveauer
- Rapporter er skabelon der er ved at blive udfyldt, og representeret ved en event-log over indtastninger

## API - udkast in progress

Overblik over nødvendig API-funktionalitet:

- Login brugernavn+password -> token
- Rapporter.
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

Det enkleste for mig er hvis data leveres som JSON. 
