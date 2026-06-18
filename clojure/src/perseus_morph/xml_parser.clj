(ns perseus-morph.xml-parser
  "Streaming SAX parser for the greek.morph.xml / latin.morph.xml format,
   ported from perseus.morph.ParseLoader$ParseHandler. Calls `on-analysis`
   once per <analysis> element with a map of {feature-name value}, where
   feature-name is the lower-cased tag name (\"form\", \"lemma\", \"orth\",
   \"pos\", \"case\", \"feature\", ...). Streams the file rather than
   building a DOM, since these files run into the hundreds of megabytes."
  (:require [clojure.java.io :as io]
            [clojure.string])
  (:import (javax.xml.parsers SAXParserFactory)
           (org.xml.sax Attributes InputSource)
           (org.xml.sax.helpers DefaultHandler)))

(defn- analysis-handler ^DefaultHandler [on-analysis]
  (let [features (java.util.HashMap.)
        current-tag (atom nil)
        current-value (StringBuilder.)]
    (proxy [DefaultHandler] []
      (startElement [_uri _local-name ^String qname ^Attributes _attrs]
        (cond
          (= qname "analysis") (.clear features)
          (= qname "analyses") nil
          :else (do (reset! current-tag (clojure.string/lower-case qname))
                    (.setLength current-value 0))))
      (endElement [_uri _local-name ^String qname]
        (cond
          (= qname "analyses") nil
          (= qname "analysis") (on-analysis (into {} features))
          :else (.put features @current-tag (.toString current-value))))
      (characters [chars start length]
        (.append current-value chars start length)))))

(defn parse-analyses!
  "Parses `filename` (a path or java.io.File), invoking `on-analysis` with
   each completed analysis's feature map as it's encountered."
  [filename on-analysis]
  (let [factory (SAXParserFactory/newInstance)
        parser (.newSAXParser factory)]
    (with-open [stream (io/input-stream filename)]
      (.parse parser (InputSource. stream) (analysis-handler on-analysis)))))
