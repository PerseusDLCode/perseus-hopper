(ns perseus-morph.walker.core
  "Walks a directory of canonical-{greek,latin}Lit / First1KGreek-style TEI
   XML files, tokenizing each Greek or Latin primary text and feeding the
   tokens through perseus-morph.frequencies.aggregator and
   perseus-morph.frequencies.document.

   Mirrors perseus.morph.MorphCodeAggregator's per-document loop
   (processToken / endDocument), but reads TEI files directly rather than
   from the original's Document/Chunk/Token database. Deliberately does not
   port MorphCodeAggregator#hasLemmasInStoplist: that method keys its
   perseus.util.Stoplist lookup on Lemma#toString(), which returns a
   multi-line debug dump (id/authorityName/headword/sequenceNumber/...),
   not the headword -- so it can never match an entry in a stoplist file of
   bare headwords, and hasLemmasInStoplist always returns false in
   practice. This port's every-token's-parses-carried-forward-as
   previous-parses behavior already matches that actual (if unintended)
   runtime behavior, so there is nothing to port here."
  (:require [clojure.java.io :as io]
            [clojure.string]
            [next.jdbc :as jdbc]
            [perseus-morph.frequencies.aggregator :as agg]
            [perseus-morph.frequencies.document :as doc-freq]
            [perseus-morph.language :as lang]
            [perseus-morph.transcoder :as transcoder]
            [perseus-morph.walker.parses :as parses])
  (:import (java.io File)
           (javax.xml.parsers SAXParserFactory)
           (org.xml.sax Attributes InputSource)
           (org.xml.sax.helpers DefaultHandler)))

(def default-excluded-tags
  "Element names whose character content is paratextual rather than primary
   text, and so should not be tokenized: editorial notes, speaker labels
   and stage directions, section headings, bibliographic citations, and
   gaps in the source. (<milestone>, <lb>, <pb> etc. need no entry here --
   they're always empty elements, with no character content of their own.)"
  #{"note" "speaker" "stage" "head" "bibl" "label" "figure" "gap" "argument"})

(def ^:private filename-language-pattern
  "Matches the CTS canonical-corpus filename convention shared by
   canonical-greekLit, canonical-latinLit, and First1KGreek alike --
   '<work>.<text>.<source>-<langcode><version?>.xml', e.g.
   'tlg0012.tlg001.perseus-grc2.xml' or 'tlg0639.tlg001.1st1K-grc1.xml'.
   Deliberately filename-based rather than reading the TEI <text
   xml:lang=...> attribute: that attribute is frequently absent or placed
   on some other descendant (a <div type=\"translation\"> for translations,
   a <listBibl> for an embedded-language citation list, ...), so it isn't a
   reliable signal across this corpus collection, whereas every primary
   text file consistently carries its language in the filename."
  #"-([a-z]{2,3})\d*\.xml$")

(def ^:private known-language-codes
  "The ISO 639 codes guess-language-code recognizes from a corpus
   filename; anything else (translations' \"eng\", multi-language
   editions' \"mul\", ...) is treated as not Greek or Latin."
  #{"grc" "lat"})

(defn guess-language-code
  "The ISO 639 language code implied by `filename`'s CTS naming convention
   (see filename-language-pattern), or nil if it doesn't look like a Greek
   or Latin primary text at all (translations, apparatus-only files,
   __cts__.xml metadata, multi-language 'mul' editions, ...) -- the signal
   this namespace uses to skip non-Greek/Latin files without even opening
   them."
  [filename]
  (when-let [[_ code] (re-find filename-language-pattern (str filename))]
    (known-language-codes code)))

(defn- tag-text-handler
  "A SAX handler that appends every bit of character data outside
   `excluded-tags` to `buffer`, in document order. A space is appended
   whenever an excluded element starts or ends, so e.g. 'word <note>...
   </note> next' doesn't collapse into a single run-on token if the source
   has no whitespace of its own immediately around the <note>."
  ^DefaultHandler [excluded-tags ^StringBuilder buffer]
  (let [excluded-depth (volatile! 0)]
    (proxy [DefaultHandler] []
      (startElement [_uri _local-name ^String qname ^Attributes _attrs]
        (when (contains? excluded-tags qname)
          (.append buffer " ")
          (vswap! excluded-depth inc)))
      (endElement [_uri _local-name ^String qname]
        (when (contains? excluded-tags qname)
          (vswap! excluded-depth dec)
          (.append buffer " ")))
      (characters [chars start length]
        (when (zero? @excluded-depth)
          (.append buffer chars start length))))))

(def ^:private word-pattern
  "Letters and combining marks only -- punctuation, digits, and whitespace
   are all token boundaries. Elision marks (Greek ᾽/’) and similar
   diacritics that aren't combining marks fall out as boundaries too,
   which matches how greek.morph.xml's own forms are recorded (e.g.
   \"mh=nin\", no elision marker)."
  #"[\p{L}\p{M}]+")

(defn extract-tokens
  "Parses `filename`'s TEI body, returning its primary text's tokens (a seq
   of Unicode word strings, in document order, skipping `excluded-tags`
   content). Does not consult the file's contents at all to decide *which*
   language it is -- see guess-language-code -- so this only needs calling
   once that's already confirmed to be \"greek\" or \"latin\"."
  [filename & {:keys [excluded-tags] :or {excluded-tags default-excluded-tags}}]
  (let [buffer (StringBuilder.)
        factory (SAXParserFactory/newInstance)
        sax-parser (.newSAXParser factory)]
    (with-open [stream (io/input-stream filename)]
      (.parse sax-parser (InputSource. stream) (tag-text-handler excluded-tags buffer)))
    (re-seq word-pattern (.toString buffer))))

(defn document-id
  "A surrogate document id for `filename`: the corpus file's own CTS-style
   basename (e.g. \"tlg0012.tlg001.perseus-grc2\"), used in place of the old
   Perseus catalog id ('Perseus:text:1999.01.0001') that
   perseus.document.Query resolved -- those ids are obsolete, and this port
   has no catalog to resolve them against anyway."
  [filename]
  (-> (File. (str filename)) .getName (clojure.string/replace #"\.xml$" "")))

(defn- token->form
  "Normalizes a raw Unicode corpus token into the same comparable string
   perseus-morph.loader.core stored as parses.form: Greek tokens go through
   Beta Code (since that's the encoding morph XML --- and so parses.form
   --- uses) before the shared per-language lowercasing."
  [language-code token]
  (lang/normalize-form language-code
                       (if (= language-code "grc")
                         (transcoder/unicode->beta-code token)
                         token)))

(defn process-tokens
  "Threads `tokens` through the aggregators, mirroring
   MorphCodeAggregator#processToken and WordFrequencyLoader#processToken
   together: each token's candidate parses (flattened across lemmas, since
   updateMorphCounts/addPriorCounts don't care which lemma a candidate parse
   belongs to) update the morph-count map for the token itself and the
   prior-count (bigram) map against the *previous* token's candidate parses,
   weighted by the same 1/n as update-morph-counts uses; the token's
   candidate *lemmas* (not yet flattened, and identified by lemma_id) separately
   update the document-count map, weighted 1/(distinct lemma count) the way
   WordFrequencyLoader's LEMMA strategy does. `lookup` is (fn [token]
   parses-grouped-by-lemma) -- it owns turning a raw corpus token into the
   comparable form parses.form was stored in (see token->form) as well as
   any caching, e.g. cached-lookup wrapping perseus-morph.walker.parses/get-parses,
   the way MorphCodeAggregator's `cachedParses` did -- so this function only
   has to know about tokens and their resulting parses, not encodings or the
   db.

   Returns {:morph-counts ... :prior-counts ... :document-counts ...}, ready
   for aggregator/write-morph-counts!, write-prior-counts!, and
   perseus-morph.frequencies.document/write-document-counts!."
  [language-code document-id lookup tokens]
  (:counts
   (reduce
    (fn [{:keys [previous-parses counts]} token]
      (let [lemma-groups (lookup token)
            current-parses (mapcat val lemma-groups)
            n (count current-parses)
            counts (-> counts
                       (update :morph-counts agg/update-morph-counts language-code current-parses)
                       (update :document-counts doc-freq/update-document-counts
                               document-id lemma-groups))
            counts (if (pos? n)
                     (update counts :prior-counts
                             (fn [prior-counts]
                               (reduce (fn [prior-counts current-parse]
                                         (agg/update-prior-counts prior-counts language-code
                                                                  previous-parses current-parse
                                                                  (/ 1.0 n)))
                                       prior-counts
                                       current-parses)))
                     counts)]
        {:previous-parses current-parses :counts counts}))
    {:previous-parses nil :counts {:morph-counts {} :prior-counts {} :document-counts {}}}
    tokens)))

(defn- cached-lookup
  "A (fn [token] parses-grouped-by-lemma) for process-tokens: normalizes
   `token` to its comparable parses.form (see token->form) and wraps
   perseus-morph.walker.parses/get-parses in a per-document cache, mirroring
   MorphCodeAggregator's `cachedParses` map (keyed there by word+languageCode
   string concatenation; a plain map keyed by the form string is equivalent
   since this cache is already scoped to one language per document)."
  [db language-code]
  (let [cache (java.util.HashMap.)]
    (fn [token]
      (let [form (token->form language-code token)]
        (or (.get cache form)
            (let [grouped (parses/get-parses db form language-code)]
              (.put cache form grouped)
              grouped))))))

(defn process-file!
  "Processes one corpus file: skips it (returning nil) unless its filename
   says it's a Greek or Latin primary text, otherwise tokenizes it and
   writes its accumulated morph/prior/document frequency counts to `db` --
   mirroring MorphCodeAggregator#endDocument and WordFrequencyLoader#endDocument's
   per-document flush, since each of these corpus files already corresponds
   to one whole document (no further chunking, the way the original's Chunk
   model allowed). The file's own basename stands in for the document id;
   see document-id.

   The three write-*! calls run inside one transaction rather than each
   upsert committing (and, under journal_mode=WAL, syncing) on its own --
   a document's worth of counts is the natural unit of \"this should all
   land or none of it should\", and batching them is far cheaper than one
   commit per row."
  [db filename]
  (when-let [language-code (guess-language-code filename)]
    (let [tokens (extract-tokens filename)
          doc-id (document-id filename)
          {:keys [morph-counts prior-counts document-counts]}
          (process-tokens language-code doc-id (cached-lookup db language-code) tokens)]
      (jdbc/with-transaction [tx db]
        (agg/write-morph-counts! tx morph-counts)
        (agg/write-prior-counts! tx prior-counts)
        (doc-freq/write-document-counts! tx document-counts))
      {:filename (str filename) :language-code language-code
       :document-id doc-id :token-count (count tokens)})))

(defn walk!
  "Walks `dir` recursively, calling process-file! on every .xml file.
   Non-Greek/Latin files (translations, __cts__.xml metadata, ...) are
   skipped via process-file!'s filename check; a file that fails to parse
   is logged and skipped rather than aborting the whole walk, since a
   multi-corpus directory like ../corpora is large enough that one
   malformed file shouldn't lose progress on the rest."
  [db dir & {:keys [log-every] :or {log-every 50}}]
  (let [xml-files (->> (file-seq (io/file dir))
                       (filter #(.isFile ^File %))
                       (filter #(clojure.string/ends-with? (.getName ^File %) ".xml")))
        files-processed (volatile! 0)
        tokens-processed (volatile! 0)]
    (doseq [file xml-files]
      (try
        (when-let [{:keys [token-count] :as result} (process-file! db file)]
          (vswap! files-processed inc)
          (vswap! tokens-processed + token-count)
          (when (zero? (mod @files-processed log-every))
            (println (format "[%5d files, %8d tokens] %s"
                             @files-processed @tokens-processed (:filename result)))))
        (catch Exception e
          (println "WARN: failed to process" (str file) "-" (.getMessage e)))))
    {:files-processed @files-processed :tokens-processed @tokens-processed}))
