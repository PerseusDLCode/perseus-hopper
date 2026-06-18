(ns perseus-morph.lexica.xml-parser
  "Streaming SAX parser for TEI dictionary XML (entry/entryFree elements
   containing sense children), ported from
   perseus.voting.SenseLoader$SenseLoaderHandler. Calls `on-sense` once per
   </sense> with {:key :id :n :level :short-def}, where :key is the
   enclosing entry's `key` attribute, :id/:n/:level are the sense's own
   attributes, and :short-def is the sense's accumulated text with each
   tr/gloss/hi child wrapped in `meaning-tag` (so a sense containing
   '<tr>foo</tr><tr>bar</tr>' with meaning-tag \"i\" yields
   \"<i>foo</i><i>bar</i>\"), or \"[no specified meaning]\" if the sense had
   no text at all. Streams the file rather than building a DOM, since these
   lexicon files are expected to be large."
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (javax.xml.parsers SAXParserFactory)
           (org.xml.sax Attributes InputSource)
           (org.xml.sax.helpers DefaultHandler)))

(defn- entry-element? [qname]
  (contains? #{"entry" "entryfree"} (str/lower-case qname)))

(defn- sense-element? [qname]
  (= (str/lower-case qname) "sense"))

(defn- meaning-element? [qname]
  (contains? #{"tr" "gloss" "hi"} (str/lower-case qname)))

(defn- sense-handler ^DefaultHandler [meaning-tag on-sense]
  (let [current-key (atom nil)
        in-sense (atom false)
        sense-attrs (atom nil)
        meaning (StringBuilder.)
        has-meaning (atom false)]
    (proxy [DefaultHandler] []
      (startElement [_uri _local-name ^String qname ^Attributes attrs]
        (cond
          (entry-element? qname)
          (reset! current-key (.getValue attrs "key"))

          (sense-element? qname)
          (do (reset! in-sense true)
              (reset! sense-attrs {:n (.getValue attrs "n")
                                    :id (.getValue attrs "id")
                                    :level (.getValue attrs "level")})
              (.setLength meaning 0)
              (reset! has-meaning false))

          (and @in-sense (meaning-element? qname))
          (do (.append meaning (str "<" meaning-tag ">"))
              (reset! has-meaning true))))

      (endElement [_uri _local-name ^String qname]
        (cond
          (sense-element? qname)
          (do (reset! in-sense false)
              (on-sense (assoc @sense-attrs
                                :key @current-key
                                :short-def (if @has-meaning
                                             (.toString meaning)
                                             "[no specified meaning]"))))

          (and @in-sense (meaning-element? qname))
          (.append meaning (str "</" meaning-tag ">"))))

      (characters [chars start length]
        (when @in-sense
          (reset! has-meaning true)
          (.append meaning chars start length))))))

(defn parse-senses!
  "Parses `filename` (a path or java.io.File), invoking `on-sense` with each
   completed sense's attributes/short-def map, wrapping tr/gloss/hi text in
   `meaning-tag`."
  [filename meaning-tag on-sense]
  (let [factory (SAXParserFactory/newInstance)
        parser (.newSAXParser factory)]
    (with-open [stream (io/input-stream filename)]
      (.parse parser (InputSource. stream) (sense-handler meaning-tag on-sense)))))
