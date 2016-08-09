(ns solsort.fmtools.macros)

(defmacro <? [expr]
  `(fmutil/throw-error (cljs.core.async/<! ~expr)))
