(ns perseus-morph.transcoder
  "Converts Beta Code (the ASCII transliteration scheme used for Greek in
   greek.morph.xml, e.g. \"*)enuw/\") into precomposed Greek Unicode, using
   the UNC Epidoc TransCoder library that the original Java app already
   depends on for Greek rendering (perseus.document.GreekFilter and
   friends) - see ../reading/lib/transcoder.jar."
  (:import (edu.unc.epidoc.transcoder TransCoder)))

(def ^:private coder
  (delay (TransCoder. "Beta Code" "Unicode Form C")))

(defn beta-code->unicode
  "Converts a Beta Code string to Unicode (NFC), or nil if given nil."
  [s]
  (when s
    (.getString ^TransCoder @coder ^String s)))
