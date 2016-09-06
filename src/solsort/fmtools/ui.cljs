(ns solsort.fmtools.ui
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]]
   [solsort.toolbox.macros :refer [<?]])
  (:require
   [solsort.fmtools.definitions :refer
    [ObjectName FieldType Columns DoubleFieldSeperator FieldValue LineType
     TaskDescription AreaGuid ObjectId PartGuid FieldGuid ColumnHeader
     TemplateGuid Description DoubleField]]
   [solsort.toolbox.misc :refer [<blob-url]]
   [solsort.toolbox.ui :refer [loading checkbox input select rot90]]
   [solsort.fmtools.db :refer [db-async! db! db server-host update-server-settings]]
   [solsort.fmtools.api-client :as api :refer [<fetch <do-fetch]]
   [solsort.fmtools.definitions :refer [field-types]]
   [solsort.fmtools.kvdb :as kvdb]
   [solsort.util
    :refer
    [<chan-seq throttle <p <ajax <seq<! js-seq normalize-css load-style! put!close!
     parse-json-or-nil log page-ready render dom->clj next-tick]]
   [reagent.core :as reagent :refer []]
   [cljs.reader :refer [read-string]]
   [clojure.data :refer [diff]]
   [clojure.string :as string :refer [replace split blank?]]
   [cljs.core.async :as async :refer [>! <! chan put! take! timeout close! pipe]]))

(defonce empty-choice "· · ·")
(defn warn [& args]
  (apply log "Warning:" args)
  nil)
(defn get-obj [id] (db [:obj id]))

;;;; Main entrypoint
(declare choose-area)
(declare choose-report)
(declare render-template)
(declare settings)
(defn app []
  (let [report (get-obj (db [:ui :report-id]))]
    [:div.main-form
     "Under development, not functional yet"
     [loading]
     (if (< 0 (db [:ui :disk]))
       [:div {:style
              {:position :absolute}} "Saving offline version to DB, do not turn off"]
       "")
     [:h1 {:style {:text-align :center}} "FM-Tools"]
     [:hr]

     [:div.ui.container
      [:div.ui.form
       [:div.field
        [:label "Område"]
        [choose-area (if (db [:ui :debug]) :root :areas)]]
       [choose-report]
       [:hr]
       [render-template report]
       [:hr]
       [settings]
       (if (db [:ui :debug])
         [:div
          [:h1 "DEBUG Log"]
          [:div (str (db [:ui :debug-log]))]]
         [:div])]]]))
(aset js/window "onerror" (fn [err] (db! [:ui :debug-log] (str err))))

;;;; Styling
(defonce unit (atom 40))
(defn style []
  (reset! unit (js/Math.floor (* 0.95 (/ 1 12) (js/Math.min 800 js/window.innerWidth))))
  (let [unit @unit]
    (load-style!
     {:#main
      {:text-align :center}

      :.line
      {:min-height 44}      :.main-form
      {:display :inline-block
       :text-align :left
       :width (* unit 12)}

      :.camera-input
      {:display :inline-block
       :position :absolute
       :right 0}

      "h1" {:clear :none}
      "h2" {:clear :none}
      "h3" {:clear :none}
      :.fmfield
      {:vertical-align :top
       :display :inline-block
       :text-align :center
       :clear :right}

      :.checkbox
      {:width 44
       :max-width "95%"
       :height 44}

      :.multifield
      {:padding-bottom 5
       :min-height 44
       :border-bottom "0.5px solid #ccc"}

      ".image-button"
      {:height 37
       :width 37
       :padding 4
       :border "2px solid black"
       :border-radius 6}
      ".camera-input img"
      {:opacity "0.5"}

      :.fields
      {:text-align :center}}
     "fmstyling"))
  (render [app]))
(aset js/window "onresize" style)
(js/setTimeout style 0)

;;; Camera button
(defn handle-file [id file]
  (go (let [image (<! (<blob-url file))
            [_ name extension]
            (or (re-find #"^(.*)([.][^.]*)$" (.-name file)) [nil "unnamed" ""])]
        #_(log 'handle-file id (db id) (butlast id) (db (butlast id)))
        (db! (butlast id)
             (into (db (butlast id) {})
                   {:id :images
                    :image-change true
                    :type :images}))
        (db! id
             (conj (db id) {"FileId" nil
                            "FileName" name
                            "FileExtension" extension
                            "LinkedToGuid" (last id)
                            :image-change true
                            :data image})))))

(defonce img-cache (reagent/atom {}))
(def img-base
  (str "https://" (server-host)
       "api/v1/Report/File?fileId="))
(defn update-images-fn []
  (go
    (<! (<chan-seq
         (for [[k v] @img-cache]
           (go
             (when-not v
               (let [base64 (get (<! (<ajax (str img-base k))) "Base64Image")]
                 (swap! img-cache assoc k
                        (str "data:image/" (cond ; truncated base64 magic number of file
                                             (= "/9" (.slice base64 0 2)) "jpg"
                                             (= "iVBORw0KGg" (.slice base64 0 10)) "png"
                                             :else "*")
                             ";base64," base64))))))))
    #_(log 'update-images @img-cache (js/Date.now))))
(def update-images (throttle update-images-fn 1000))
(defn camera-button [id]
  (let [show-controls (get (db [:ui :show-controls]) id)
        images (db id)]
    [:div
     (if show-controls
       {:style {:border-left "3px solid gray"
                :border-top "3px solid gray"
                :padding-left "5px"
                :padding-top "3px"}}
       {})
     [:div.camera-input
      [:img.image-button
       {:src (if (< 0 (count images))
               "assets/photos.png"
               "assets/camera.png")
        :on-click #(db-async! [:ui :show-controls id] (not show-controls))}]]
     (if show-controls
       [:div {:style {:padding-right 44}}
        [:label {:for id}
         [:img.image-button {:src "assets/camera.png"}]]
        [:input
         {:type "file" :accept "image/*"
          :id id :style {:display :none}
          :on-change #(handle-file id (aget (.-files (.-target %1)) 0))}]
        " \u00a0 "
        (into
         [:span.image-list]
         (for [img (remove :deleted images)]
           [:div {:style {:display :inline-block
                          :position :relative
                          :max-width "60%"
                          :min-width 44
                          :margin 3
                          :vertical-align :top}}
            [:img.image-button
             {:src "./assets/delete.png"
              :style {:position :absolute
                      :top 0
                      :right 0
                      :background "rgba(255,255,255,0.7)"}
              :on-click (fn []
                          (db! (concat (butlast id) [:image-change]) true)
                          (db!
                           id
                           (doall
                            (map #(if (= % img)
                                    (into % {:deleted true
                                             :image-change true
                                             :data ""})
                                    %) (db id [])))))}]
            [:img {:src
                   (do
                     (when (and (get img "FileId")
                                (not (get @img-cache (get img "FileId"))))
                       (swap! img-cache assoc (get img "FileId") false)
                       (update-images 1000))
                     (or (:data img)
                         (get @img-cache (get img "FileId"))
                         "assets/not-loaded.png"))
                   :style {:max-height 150
                           :vertical-align :top
                           :max-width "100%"}}]]))]
       "")]))

;;;; Area/report chooser
(defn- sub-areas "used by traverse-areas" [id]
  (let [area (get-obj id)]
    (apply concat [id] (map sub-areas (:children area)))))
(defn traverse-areas "find all childrens of a given id" [id]
  (let [selected (db [:ui id])]
    (if selected
      (into [id] (traverse-areas selected))
      (sub-areas id))))
(defn choose-area-name [obj]
  (str (or
        (get obj "ObjectName")
        (get obj "ReportName")
        (get obj "Name")
        (get obj "Description")
        (get obj "TaskDescription")
        (:id obj))))
(defn choose-area [id]
  (let [o (get-obj id)
        children (:children o)
        selected (db [:ui id])
        child (get-obj selected)]
    (when (and (db [:ui :debug])
               (or (and children (not selected))
                   (and (not children) (:id o))))
      #_(log 'choosen-area (choose-area-name o) o (count children)))
    (if children
      [:div
       [select
        {:db [:ui id]
         :options (concat
                   [[empty-choice]]
                   (sort (for [child-id children]
                           [(choose-area-name (get-obj child-id))
                            child-id])))}]
       [choose-area selected]]
      [:div])))
(defn find-area [id]
  (let [selected (db [:ui id])]
    (if selected
      (find-area selected)
      id)))
(defn all-reports []
  (map get-obj (:children (get-obj :reports))))
(defn all-templates []
  (map get-obj (:children (get-obj :templates))))

(defn current-open-reports [areas]
  (filter #((into #{} areas) (% "ObjectId"))
          (all-reports)))

(defn list-available-templates [areas]
  (let [templates (all-templates)
        obj (get-obj (find-area :areas))
        area-guid (get obj "AreaGuid")
        templates (filter #((into #{} (get % "ActiveAreaGuids")) area-guid) templates)
        open-templates (into #{} (map #(get % "TemplateGuid")
                                      (current-open-reports
                                       areas)))
        templates (remove #(open-templates (:id %)) templates)]
    (if (= :object (:type obj))
      templates
      [])))
(defn create-report [obj-id template-id name]
  (go
    (db! [:ui :new-report-name] "")
    ;(log 'create-report obj-id template-id name)
    (let [creation-response (<! (<ajax
                                 (str "https://"
                                      (server-host)
                                      "/api/v1/"
                                      "Report?objectId=" obj-id
                                      "&templateGuid=" template-id
                                      "&reportName=" name)
                                 :method "POST"))
          new-report-id (get creation-response "ReportGuid")]
      (if new-report-id
        (do
          (<! (<fetch))
          (db! [:ui :report-id] new-report-id))
        (warn "failed making new report" obj-id template-id name creation-response)))))
(defn finish-report [report-id]
  (go
    ;(log 'finish-report)
    (let [response (<! (<ajax ; TODO not absolute url
                        (str "https://"
                             (server-host)
                             "/api/v1/"
                             "Report?ReportGuid=" report-id)
                        :method "PUT"))]
      #_(log 'finish-report-response response))))
(defn render-report-list [reports]
  [:div.field
   [:label "Rapport"]
   [select
    {:db [:ui :report-id]
     :options (concat
               [[empty-choice]]
               (sort (for [report reports]
                       [(report "ReportName")
                        (report "ReportGuid")])))}]
   (if ((into #{} (map :id reports)) (db [:ui :report-id]))
     [:p {:style {:text-align :right}}
      [:button.ui.red.button
       {:on-click #(finish-report (db [:ui :report-id]))}
       "Afslut rapport"]]
     "")])
(defn choose-report "react component listing reports" []
  (let [areas (doall (traverse-areas :areas))
        reports (current-open-reports areas)
        available-templates (list-available-templates areas)]
    [:div
     (case (count reports)
       0 (do
           (db-async! [:ui :report-id] nil)
           ""
          ;(render-report-list reports)
           #_[:span.empty])
       1 (do
           (db-async! [:ui :report-id] ((first reports) "ReportGuid"))
           (render-report-list reports)
           #_[:div "Rapport: " ((first reports) "ReportName")])
       (render-report-list reports))
     (if (or (not (empty? reports)) (empty? available-templates))
       ""
       [:div.field
        [:label "Opret rapport"]
        [:p [input {:db [:ui :new-report-name]}]]
        (into [:div {:style {:text-align :right}}]
              (map
               (fn [template]
                 [:button.ui.button
                  {:on-click #(create-report (find-area :areas) (:id template) (db [:ui :new-report-name]))}
                  (get template "Name")])
               available-templates))
        #_(str (map key (list-available-templates)))])]))

;;;; Actual report
(def do-rot90 (not= -1 (.indexOf js/location.hash "rot90")))
(defn data-id [k]
  [:obj (or (db [:entries k]) :missing-data-object)])
(def field-name "mapping from field-type to value name in api"
  {:approve-reject "String"
   :time "TimeSpan"
   :text-input-noframe "String"
   :text-input "String"
   :date "DateTime"
   :decimal-2-digit "Double"
   :checkbox "Boolean"
   :remark "String"})
(defn single-field [obj cols id area pos]
  (let [field-type (FieldType obj)
        value (FieldValue obj)
        id (conj id (str (field-name field-type) "Value" pos))]
    (case field-type
      :fetch-from (str (ObjectName area))
      :approve-reject [select {:db id
                               :options {"" ""
                                         "Godkendt" "Approved"
                                         "Afvist" "Rejected"
                                         "-" "None"}}]
                                        ; TODO: not checkbox - string value "Approved" "Rejected" "None" ""
      :text-fixed (if do-rot90 [rot90 [:span value]] [:span value])
      :time [input {:db id :type :time}]
      :remark [input {:db id}]
      :text-input-noframe [input {:db id}]
      :text-input [input {:db id}]
      :date [input {:db id :type "datetime"}]
      :decimal-2-digit [input {:db id :type "number"}]
      :decimal-4-digit [input {:db id :type "number"}]
      :integer [input {:db  id :type "number"}]
      :checkbox [checkbox {:db id}]
      :text-fixed-noframe [:span value]
      [:strong "unhandled field:" (str field-type) " " value])))
(defn field [obj-id cols id area]
  (let [obj (get-obj obj-id)
        columns (Columns obj)
        double-field (DoubleField obj)
        double-separator (DoubleFieldSeperator obj)
        id (data-id id)]
    [:span.fmfield {:key id
                    :style {:width (- (* 12 @unit (/ columns cols)) (/ 50 cols))}
                    :on-click (fn [] (log 'debug {:obj obj :id id :id-obj (db id)}) nil)}
     (if double-field
       (let [obj (dissoc obj "DoubleField")]
         [:span (single-field obj cols id area 1)
          " " double-separator " "
          (single-field obj cols id area 2)])
       (single-field obj cols id area 1))]))

(defn template-control [id line-id position report-id obj-id]
  (let [ctl (get-obj id)
        title (get ctl "Title")
        series (filter #(not= "" (get ctl (str "ChartSerieName" %))) (range 1 6))
        line (get-obj line-id)
        data-id [report-id obj-id (second line-id)]]
    [:div
     [:h3 position " " title]
     (into
      [:div]
      (for [serie (concat [0] series)]
        [:div.multifield
         (get ctl (str "ChartSerieName" serie))
         (into [:div]
               (for [x (range
                        (ctl "XAxisMin")
                        (+ (ctl "XAxisMax") (ctl "XAxisStep"))
                        (ctl "XAxisStep"))]
                 (let [i (+ (* 1000 serie)
                            (inc (/ (- x (ctl "XAxisMin"))
                                    (ctl "XAxisStep"))))
                       control-data-id (db [:entries (conj data-id i)])]
                   ;(log 'serie serie x i (get-obj control-data-id))
                   [:span {:style {:display :inline-block
                                   :text-align :center
                                   :width 60}}
                    (if (= serie 0)
                      (str x)
                      [input {:db  [:obj control-data-id "IntegerValue1"]
                              :type "number"}])])))]))]))
(defn render-line [line-id report-id obj]

  (let [line (get-obj line-id)
        line-type (LineType line)
        cols (get line "ColumnsTotal")
        desc (str (get line "Position" "") " " (TaskDescription line))
        area (AreaGuid line)
        obj-id (ObjectId obj)
        id (data-id [report-id obj-id (PartGuid line)])
        debug-str (dissoc line :fields)
        debug-str (str [report-id obj-id (PartGuid line) id])
        data-id (db [:entries id])
        cam  [camera-button (concat (butlast id) [:images] [(last id)])]
        fields (into
                [:div.fields]
                (map #(field % cols [report-id obj-id %] obj)
                     (:children line)))]
    [:div.line
     {:style
      {:padding-top 10}
      :key [obj-id line-id]
      :on-click #(log 'debug debug-str)}
     (case line-type
       :template-control [template-control (get line "ControlGuid") id (get line "Position" "") report-id obj-id]
       :basic [:h3 "" desc]
       :simple-headline [:h3 cam desc]
       :vertical-headline [:div [:h3 cam desc] fields]
       :horizontal-headline [:div [:h3 cam desc] fields]
       :multi-field-line [:div.multifield cam desc fields]
       :description-line [:div cam desc [input {:db (conj id "Remarks") :type :text}]]
       [:span {:key id} "unhandled line " (str line-type) " " debug-str])]))
(defn render-section [lines report-id areas]
  (doall (for [obj areas]
           (doall (for [line lines]
                    (when (= (AreaGuid line) (AreaGuid obj))
                      (render-line (get line "PartGuid") report-id obj)))))))
(defn render-lines
  [lines report-id areas]
  (apply concat
         (for [section (partition-by ColumnHeader lines)]
           (render-section section report-id areas))))
(defn render-template [report]
  (let [id (TemplateGuid report)
        template (get-obj id)
        areas (conj (doall (map get-obj (traverse-areas (ObjectId report)))) {})
        max-objects 100]
    (into
     [:div.ui.form
      [:h1 (Description template)]
      (if (< max-objects (count areas))
        [:div
         [:div {:style {:display :inline-block :float :right}} [checkbox [:ui :nolimit]]]
         [:br]
         "Vis rapportindhold for områder med mere end " (str max-objects) " objekter (langsomt):"
         [:br]
         "- eller vælg underområde herover."]
        "")]
     (if (and (< max-objects (count areas)) (not (db [:ui :nolimit])))
       []
       (render-lines (map get-obj (:children template)) (:id report) areas)))))

;;;; Settings
(when-not (db [:obj :settings :server]) (update-server-settings))

(defn settings []
  [:div
   [:h1 "Indstillinger"]
   [:p "Server: "
    [:code (server-host)] [:br] [:span.blue.ui.button
                                 {:on-click
                                  (fn [])}
                                 "Ændr server"]
    #_[input {:db [:obj :config :server]}]]
   [:p [checkbox [:ui :debug]] "debug enabled"]
   [:span.blue.ui.button {:on-click #(<do-fetch)} "reload"]
   [:span.red.ui.button
    {:on-click
     #(go
        (try
          (<! (kvdb/clear))
          (db! [] {})
          (js/location.reload)
          (catch js/Error e (js/console.log e))))}
    "reset + reload"]])
