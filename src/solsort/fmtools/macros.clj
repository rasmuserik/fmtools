(ns solsort.fmtools.macros)

; requires [solsort.fmtools.util :as fmutil]

(defmacro <? [expr]
  `(fmutil/throw-error (cljs.core.async/<! ~expr)))
