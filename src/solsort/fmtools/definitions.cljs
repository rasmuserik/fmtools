(ns solsort.fmtools.definitions)

(def field-paths
  {"StringValue1" [:string]
   "StringValue2" [:field2 :string]
   "BooleanValue1" [:boolean]
   "BooleanValue2" [:field2 :boolean]
   "IntegerValue1" [:integer]
   "IntegerValue2" [:field2 :integer]
   "DoubleValue1" [:double]
   "DoubleValue2" [:field2 :double]
   "DateTimeValue1" [:date]
   "TimeSpanValue1" [:time]})
(def trail-types
  {0 :none
   1 :object
   2 :area
   3 :user
   4 :template-enabled
   5 :template-disabled
   6 :template-changed
   7 :part-changed
   8 :part-image
   9 "StringValue1"
   10 "StringValue2"
   11 "BooleanValue1"
   12 "BooleanValue2"
   13 "IntegerValue1"
   14 "IntegerValue2"
   15 "DoubleValue1"
   16 "DoubleValue2"
   17 "DateTimeValue1"
   18 "TimeSpanValue1"})
(def full-sync-types
  #{:object :area :user :template-enabled :template-disabled :template-changed :part-changed :part-image-changed})
(defonce field-types
  {:text-fixed :string
   :text-input :string
   :checkbox :boolean
   :integer :integer
   :decimal-2-digit :double
   :decimal-4-digit :double
   :date :date
   :time :time
   :text-fixed-noframe :string
   :text-input-noframe :string
   :approve-reject :boolean
   :remark :string})
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

(defn AreaGuid "Gets \"AreaGuid\"" [o] (get o "AreaGuid"))
(defn AreaName "Gets \"AreaName\"" [o] (get o "AreaName"))
(defn Areas "Gets \"Areas\"" [o] (get o "Areas"))
(defn ColumnHeader "Gets \"ColumnHeader\"" [o] (get o "ColumnHeader"))
(defn Columns "Gets \"Columns\"" [o] (get o "Columns"))
(defn Description "Gets \"Description\"" [o] (get o "Description"))
(defn DisplayOrder "Gets \"DisplayOrder\"" [o] (get o "DisplayOrder"))
(defn FieldGuid "Gets \"FieldGuid\"" [o] (get o "FieldGuid"))
(defn FieldType "Gets \"FieldType\"" [o] (get o "FieldType"))
(defn LineType "Gets \"LineType\"" [o] (get o "LineType"))
(defn FieldValue "Gets \"FieldValue\"" [o] (get o "FieldValue"))
(defn Name "Gets \"Name\"" [o] (get o "Name"))
(defn DoubleFieldSeperator "Gets \"DoubleFieldSeperator\"" [o] (get o "DoubleFieldSeperator"))
(defn DoubleField "Gets \"DoubleField\"" [o] (get o "DoubleField"))
(defn ObjectId "Gets \"ObjectId\"" [o] (get o "ObjectId"))
(defn ObjectName "Gets \"ObjectName\"" [o] (get o "ObjectName"))
(defn Objects "Gets \"Objects\"" [o] (get o "Objects"))
(defn ParentId "Gets \"ParentId\"" [o] (get o "ParentId"))
(defn PartGuid "Gets \"PartGuid\"" [o] (get o "PartGuid"))
(defn PartType "Gets \"PartType\"" [o] (get o "PartType"))
(defn ReportFields "Gets \"ReportFields\"" [o] (get o "ReportFields"))
(defn ReportGuid "Gets \"ReportGuid\"" [o] (get o "ReportGuid"))
(defn ReportName "Gets \"ReportName\"" [o] (get o "ReportName"))
(defn ReportTables "Gets \"ReportTables\"" [o] (get o "ReportTables"))
(defn ReportTable "Gets \"ReportTable\"" [o] (get o "ReportTable"))
(defn ReportTemplateTable "Gets \"ReportTemplateTable\"" [o] (get o "ReportTemplateTable"))
(defn ReportTemplateFields "Gets \"ReportTemplateFields\"" [o] (get o "ReportTemplateFields"))
(defn ReportTemplateParts "Gets \"ReportTemplateParts\"" [o] (get o "ReportTemplateParts"))
(defn TaskDescription "Gets \"TaskDescription\"" [o] (get o "TaskDescription"))
(defn TemplateGuid "Gets \"TemplateGuid\"" [o] (get o "TemplateGuid"))
