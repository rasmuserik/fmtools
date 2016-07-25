(ns solsort.fmtools.ui
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require
   [solsort.fmtools.definitions :refer
    [ObjectName FieldType Columns DoubleFieldSeperator FieldValue LineType
     TaskDescription AreaGuid ObjectId PartGuid FieldGuid ColumnHeader
     TemplateGuid Description DoubleField]]
   [solsort.fmtools.util :refer [clj->json json->clj third to-map delta empty-choice <chan-seq <localforage fourth-first]]
   [solsort.misc :refer [<blob-url]]
   [solsort.fmtools.db :refer [db db!]]
   [solsort.fmtools.definitions :refer [field-types]]
   [solsort.util
    :refer
    [<p <ajax <seq<! js-seq normalize-css load-style! put!close!
     parse-json-or-nil log page-ready render dom->clj next-tick]]
   [reagent.core :as reagent :refer []]
   [cljs.reader :refer [read-string]]
   [clojure.data :refer [diff]]
   [re-frame.core :as re-frame
    :refer [register-sub subscribe register-handler
            dispatch dispatch-sync]]
   [clojure.string :as string :refer [replace split blank?]]
   [cljs.core.async :as async :refer [>! <! chan put! take! timeout close! pipe]]))

;;;; Main entrypoint
(declare loading)
(declare choose-area)
(declare choose-report)
(declare render-template)
(defn app []
  (let [report @(subscribe [:db :reports @(subscribe [:ui :report-id])])]
    [:div.main-form
     "Under development, not functional yet"
     [loading]
     [:h1 {:style {:text-align :center}} "FM-Tools"]
     [:hr]
     [:div.ui.container
      [:div.ui.form
       [choose-area report]
       [choose-report]]]
     [:hr]
     #_[render-template @(subscribe [:ui :current-template])]
     [render-template report]
     [:hr]
     [:div "DEBUG"]
     [:div (str @(db :ui :debug))]]))
(aset js/window "onerror" (fn [err] (db! :ui :debug (str err))))

;;;; Styling
(defonce unit (atom 40))
(defn style []
  (reset! unit (js/Math.floor (* 0.95 (/ 1 12) (js/Math.min 800 js/window.innerWidth))))
  (let [unit @unit]
    (load-style!
     {:#main
      {:text-align :center}

      :.line
      {;:min-height 44
       }

      :.main-form
      {:display :inline-block
       :text-align :left
       :width (* unit 12)}

      :.camera-input
      {:display :inline-block
       :position :absolute
       :right 0}

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

;;;; Generic Components
(defn loading "simple loading indicator, showing when (db :ui :loading)" []
  (if @(subscribe [:db :loading])
    [:div
     {:style {:position :fixed
              :display :inline-block
              :top 0 :left 0
              :width "100%"
              :heigth "100%"
              :background-color "rgba(0,0,0,0.6)"
              :color "white"
              :z-index 100
              :padding-top (* 0.3 js/window.innerHeight)
              :text-align "center"
              :font-size "48px"
              :text-shadow "2px 2px 8px #000000"
              :padding-bottom (* 0.7 js/window.innerHeight)}}
     "Loading..."]
    [:span]))
(defn select [id options]
  (let [current @(subscribe [:ui id])]
    (into [:select
           {:value (prn-str current)
            :onChange
            #(dispatch [:ui id (read-string (.-value (.-target %1)))])}]
          (for [[k v] options]
            (let [v (prn-str v)]
              [:option {:key v :value v} k])))))
(defn checkbox [id]
  (let [value @(apply db id)]
    [:img.checkbox
     {:on-click #(apply db! (concat id [(not value)]))
      :src (if value "assets/check.png" "assets/uncheck.png")}]))
(defn input  [id & {:keys [type size max-length options]
                    :or {type "text"}}]
  (case type
    :select (select id options)
    :checkbox (checkbox id)
    [:input {:type type
             :style {:padding-right 0
                     :padding-left 0
                     :text-align :center
                     :overflow :visible}
             :name (prn-str id)
             :key (prn-str id)
             :size size
             :max-length max-length
             :value @(apply db id)
             :on-change #(apply db! (concat id [(.-value (.-target %1))]))}]))
(defn- fix-height "used by rot90" [o]
  (let [node (reagent/dom-node o)
        child (-> node (aget "children") (aget 0))
        width (aget child "clientHeight")
        height (aget child "clientWidth")
        style (aget node "style")]
    (aset style "height" (str height "px"))
    (aset style "width" (str width "px"))))
(def rot90 "reagent-component rotating its content 90 degree"
  (with-meta
    (fn [elem]
      [:div
       {:style {:position "relative"
                :display :inline-block}}
       [:div
        {:style {:transform-origin "0% 0%"
                 :transform "rotate(-90deg)"
                 :position "absolute"
                 :top "100%"
                 :left 0
                 :display :inline-block}}
        elem]])
    {:component-did-mount fix-height
     :component-did-update fix-height}))

;;; Camera button
(defn handle-file [id file]
  (go (dispatch [:add-image id (<! (<blob-url file))])))
(defn camera-button [id]
  (let [show-controls (get @(db :ui :show-controls) id)
        images @(apply db id)]
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
        :on-click #(db! :ui :show-controls id (not show-controls))}]]
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
         (for [img images]
           [:div {:style {:display :inline-block
                          :position :relative
                          :max-width "60%"
                          :margin 3
                          :vertical-align :top}}
            [:img.image-button
             {:src "./assets/delete.png"
              :style {:position :absolute
                      :top 0
                      :right 0
                      :background "rgba(255,255,255,0.7)"}
              :on-click #(dispatch [:remove-image id img])}]
            [:img {:src img
                   :style {:max-height 150
                           :vertical-align :top
                           :max-width "100%"}}]]))]
       "")]))

;;;; Area/report chooser
(defn- sub-areas "used by traverse-areas" [id]
  (let [area @(subscribe [:area-object id])]
    (apply concat [area] (map sub-areas (keys (:children area))))))
(defn traverse-areas "find all childrens of a given id" [id]
  (let [selected @(subscribe [:ui id])]
    (if selected
      (into [@(subscribe [:area-object id])] (traverse-areas selected))
      (sub-areas id))))
(defn set-areas! "used by choose-area" [oid]
  (log 'set-areas oid)
  (let [obj @(db :objects oid)
        parent-id (get obj "ParentId")]
    (when parent-id
      (db! :ui (if (= 0 parent-id) :root parent-id) oid)
      (recur parent-id))))
(defn areas "used by choose-report and choose-area" [id]
  (let [obj @(subscribe [:area-object id])
        children (:children obj)
        selected @(subscribe [:ui id])
        child @(subscribe [:area-object selected])]
    (if children
      [:div
       [select id
        (concat [[empty-choice]]
                (sort-by first 
                         (for [[child-id] children]
                           [(.trim (str (ObjectName @(subscribe [:area-object child-id])))) child-id])))]
       (areas selected)]
      [:div])))
(defn choose-area "react component listing areas" [report]
  #_(if (:children @(subscribe  [:area-object (ObjectId report)]))
      [:div.field
       [:label "Område"]
       [areas (or (ObjectId report) :root)]]
      [:span.empty])
  (when (ObjectId report) (set-areas! (ObjectId report)))
  [:div.field
   [:label "Område"]
   [areas :root]])
(defn choose-report "react component listing reports" []
  (let [areas (into #{} (map ObjectId (traverse-areas :root)))
        reports (filter #(areas (% "ObjectId")) (map second @(db :reports)))]
    (case (count reports)
      0 (do
          (db! :ui :report-id nil)
          [:span.empty])
      1 (do
          (db! :ui :report-id ((first reports) "ReportGuid"))
          [:div "Rapport: " ((first reports) "ReportName")])
      [:div.field
       [:label "Rapport"]
       [select :report-id
        (concat [[empty-choice]]
                (for [report reports]
                  [(report "ReportName")
                   (report "ReportGuid")]))]])))

;;;; Actual report
(def do-rot90 (not= -1 (.indexOf js/location.hash "rot90")))
(defn field [obj cols id area]
  (let [field-type (FieldType obj)
        columns (Columns obj)
        double-field (DoubleField obj)
        double-separator (DoubleFieldSeperator obj)
        value (FieldValue obj)]
    [:span.fmfield {:key id
                    :style {:width (- (* 12 @unit (/ columns cols)) (/ 50 cols))}
                    :on-click (fn [] (log obj) false)}
     (if double-field
       (let [obj (dissoc obj "DoubleField")]
         [:span [field obj cols id]
          " " double-separator " "
          [field obj cols (conj id :field-2)]])
       (let [id (conj id (field-types field-type))]
         (case field-type
           :fetch-from (str (ObjectName area))
           :approve-reject [checkbox id]
           :text-fixed (if do-rot90 [rot90 [:span value]] [:span value])
           :time [input id :type :time]
           :remark [input id]
           :text-input-noframe [input id]
           :text-input [input id]
           :decimal-2-digit [input id :size 2 :max-length 2 :type "number"]
           :checkbox [checkbox id]
           :text-fixed-noframe [:span value]
           [:strong "unhandled field:" (str field-type) " " value])))]))
(defn template-control [line line-id]
  (let [id (get line "ControlGuid")
        ctl @(db :controls id)
        title (get ctl "Title")
        series (filter #(not= "" (get ctl (str "ChartSerieName" %))) (range 1 6))]
    [:div
     [:h3 (get line "Position" "") " " title]
     (into
      [:div]
      (for [serie (concat [0] series)]
        [:div.multifield
         (get ctl (str "ChartSerieName" serie))
         (into [:div]
               (for [i (range
                        (ctl "XAxisMin")
                        (+ (ctl "XAxisMax") (ctl "XAxisStep"))
                        (ctl "XAxisStep"))]
                 [:span {:style {:display :inline-block
                                 :text-align :center
                                 :width 60}}
                  (if (= serie 0)
                    (str i)
                    [input (concat line-id [:control serie i])
                     :type "number"])]))]))]))
(defn render-line [line report-id obj]
  (let [line-type (LineType line)
        cols (apply + (map Columns (:fields line)))
        desc (str (get line "Position" "") " " (TaskDescription line))
        debug-str (dissoc line :fields)
        area (AreaGuid line)
        obj-id (ObjectId obj)
        id [:data report-id obj-id (PartGuid line)]
        fields (into
                [:div.fields]
                (map #(field % cols [:data report-id obj-id (FieldGuid %)] obj)
                     (:fields line)))]
    [:div.line
     {:style
      {:padding-top 10}
      :key id
      :on-click #(log debug-str)}
     (case line-type
       :template-control [template-control line id]
       :basic [:h3 "" desc]
       :simple-headline [:h3 desc]
       :vertical-headline [:div [:h3 desc] fields]
       :horizontal-headline [:div [:h3 desc] fields]
       :multi-field-line [:div.multifield desc [camera-button (conj id :images)]
                          fields]
       :description-line [:div desc [input  (conj id :description) {:type :text}]]
       [:span {:key id} "unhandled line " (str line-type) " " debug-str])]))
(defn render-section [lines report-id areas]
  (for [obj areas]
    (for [line lines]
      (when (= (AreaGuid line) (AreaGuid obj))
        (render-line line report-id obj)))))
(defn render-lines
  [lines report-id areas]
  (apply concat
         (for [section (partition-by ColumnHeader lines)]
           (render-section section report-id areas))))
(defn render-template [report]
  (let [id (TemplateGuid report)
        template @(subscribe [:template id])
        report-id @(subscribe [:ui :report-id])
        areas (conj (traverse-areas (ObjectId report)) {})
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
     (if (and (< max-objects (count areas)) (not @(subscribe [:ui :nolimit])))
       []
       (render-lines (:rows template) report-id areas)
       #_(doall (map #(line % report-id areas) (:rows template)))))))

