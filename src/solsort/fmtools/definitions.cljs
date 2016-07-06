(ns solsort.fmtools.definitions)

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
