(ns perseus-morph.transcoder
  "Converts between Beta Code (the ASCII transliteration scheme used for
   Greek in greek.morph.xml, e.g. \"*)enuw/\") and precomposed Greek Unicode,
   using the UNC Epidoc TransCoder library that the original Java app
   already depends on for Greek rendering (perseus.document.GreekFilter and
   friends) - see ../reading/lib/transcoder.jar."
  (:import (edu.unc.epidoc.transcoder TransCoder)))

(def ^:private decoder
  (delay (TransCoder. "Beta Code" "Unicode Form C")))

(def ^:private encoder
  "\"Perseus Beta Code\" (not the plain \"Beta Code\" converter) is the
   convention that actually matches greek.morph.xml's lowercase-ASCII
   forms (e.g. \"mh=nin\"); the plain converter emits uppercase ASCII for
   the same input, which would never match a parses.form lookup."
  (delay (TransCoder. "Unicode" "Perseus Beta Code")))

(defn beta-code->unicode
  "Converts a Beta Code string to Unicode (NFC), or nil if given nil."
  [s]
  (when s
    (.getString ^TransCoder @decoder ^String s)))

(defn unicode->beta-code
  "Converts a Unicode Greek string to Beta Code, or nil if given nil. Used
   to turn corpus text (Unicode) into the form ParseLoader stored
   parses.form in (Beta Code), so corpus tokens can be looked up there."
  [s]
  (when s
    (.getString ^TransCoder @encoder ^String s)))
