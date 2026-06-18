(ns perseus-morph.lexica.core
  "Loads a TEI lexicon XML file (entry/entryFree with sense children) into
   SQLite, ported from perseus.voting.SenseLoader. Each <sense> becomes one
   row in the `senses` table, keyed by its entry/sense number pair (parsed
   out of the sense's `id` attribute, e.g. \"n12.34\" -> entry 12, sense 34)
   plus the lexicon's document id."
  (:require [next.jdbc :as jdbc]
            [perseus-morph.lexica.xml-parser :as xml-parser]))

(def ^:private id-pattern
  "Mirrors perseus.voting.VoteManager.ID_PATTERN."
  #"n(\d+)\.(\d+)")

(def ^:private meaning-tags
  "Mirrors SenseLoaderHandler's constructor: which markup tag wraps each
   tr/gloss/hi child's text, keyed by lexicon document id. Lexicons not
   listed here default to \"i\" (plain italics)."
  {"Perseus:text:1999.04.0057" "g"
   "Perseus:text:1999.04.0058" "g"
   "Perseus:text:1999.04.0072" "g"
   "Perseus:text:1999.04.0073" "g"
   "Perseus:text:1999.04.0059" "l"
   "Perseus:text:1999.04.0060" "l"})

(defn meaning-tag [lexicon-id]
  (get meaning-tags lexicon-id "i"))

(defn- parse-ids
  "Splits a sense's `id` attribute (e.g. \"n12.34\") into [entry-id
   sense-id], or [-1 -1] (with a warning) if it doesn't match, mirroring
   SenseLoader.insertSense's handling of a malformed id."
  [id]
  (if-let [[_ entry sense] (re-matches id-pattern (or id ""))]
    [(Integer/parseInt entry) (Integer/parseInt sense)]
    (do (println "WARN: Error getting IDs for" id)
        [-1 -1])))

(defn- truncate-short-def
  "The short_definition column is 100 chars; longer text is cut to 97 chars
   plus an ellipsis, possibly leaving a tag open (SenseLoader.insertSense
   accepted the same imprecision)."
  [short-def]
  (if (> (count short-def) 100)
    (str (subs short-def 0 97) "...")
    short-def))

(defn clear-existing!
  "Deletes any existing senses for `lexicon-id`, mirroring
   SenseLoader.clearExisting."
  [db lexicon-id]
  (jdbc/execute! db ["DELETE FROM senses WHERE document_id = ?" lexicon-id]))

(defn- insert-sense! [db row]
  (jdbc/execute! db
                 ["INSERT INTO senses
                     (entry_id, sense_id, document_id, lemma, sense, level, short_definition)
                   VALUES (?, ?, ?, ?, ?, ?, ?)"
                  (:entry-id row) (:sense-id row) (:document-id row)
                  (:lemma row) (:sense row) (:level row) (:short-definition row)]))

(defn load!
  "Streams `filename`'s senses, inserting one `senses` row per <sense>.
   `lexicon-id` is the lexicon's Perseus document id (used as the senses
   table's document_id, and to look up the meaning-tag for this lexicon)."
  [db lexicon-id filename]
  (let [sense-count (volatile! 0)]
    (xml-parser/parse-senses!
     filename (meaning-tag lexicon-id)
     (fn [{:keys [key id n level short-def]}]
       (let [[entry-id sense-id] (parse-ids id)]
         (insert-sense! db
                        {:entry-id entry-id
                         :sense-id sense-id
                         :document-id lexicon-id
                         :lemma (str "entry=" key)
                         :sense n
                         :level (if level (Integer/parseInt level) -1)
                         :short-definition (truncate-short-def short-def)})
         (vswap! sense-count inc))))
    {:senses @sense-count}))
