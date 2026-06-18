(ns perseus-morph.frequencies.document
  "DDL and counting for per-document lemma frequency, the SQLite analog of
   perseus.ie.freq.EntityDocumentFrequency/WordFrequencyLoader, used by
   perseus.eval.morph.WordFrequencyEvaluator to disambiguate a token's parses
   by how common its candidate lemmas are *in this document specifically*
   (as opposed to perseus-morph.frequencies.aggregator's corpus-wide counts).

   Only weighted_frequency is ported: WordFrequencyEvaluator never reads
   WordFrequencyLoader's maxFrequency/minFrequency/termFreq/tfidf, which
   exist to support other features (key-term extraction, WordFreqController)
   this port doesn't implement.

   Rows are keyed by a document id rather than the old Perseus catalog id
   ('Perseus:text:1999.01.0001', resolved via perseus.document.Query) --
   those ids are obsolete, so perseus-morph.walker uses the corpus
   file's own CTS-style filename instead; see
   perseus-morph.walker/document-id."
  (:require [next.jdbc :as jdbc]))

(def ddl
  ["CREATE TABLE IF NOT EXISTS document_frequencies (
      language_code TEXT NOT NULL,
      document_id TEXT NOT NULL,
      headword TEXT NOT NULL,
      sequence_number INTEGER NOT NULL DEFAULT -1,
      weighted_frequency REAL NOT NULL DEFAULT 0,
      UNIQUE (language_code, document_id, headword, sequence_number)
    )"
   "CREATE INDEX IF NOT EXISTS idx_document_frequencies_doc
      ON document_frequencies (language_code, document_id)"])

(defn init-db!
  "Creates the document_frequencies table (and its index) if it doesn't
   already exist. Additive: does not touch any other table."
  [db]
  (doseq [stmt ddl]
    (jdbc/execute! db [stmt])))

(defn update-document-counts
  "Given one token's candidate parses grouped by lemma (as
   perseus-morph.walker.parses/get-parses returns, {[headword sequence-number]
   [parse-row ...]}), accumulates `1 / (count lemma-groups)` into `counts`
   (a map of {[language-code document-id headword sequence-number] count})
   for every candidate lemma -- mirroring WordFrequencyLoader's LEMMA
   TokenStrategy, where each of a token's Lemmatizer.getLemmas results gets
   `tuple.count(lemmas.size())`.

   Unlike perseus-morph.frequencies.aggregator/update-morph-counts, this
   weights by the number of *distinct candidate lemmas*, not parses: a token
   with two parses that share one lemma counts as unambiguous here."
  [counts language-code document-id lemma-groups]
  (if (empty? lemma-groups)
    counts
    (let [weight (/ 1.0 (count lemma-groups))]
      (reduce (fn [counts [headword sequence-number]]
                (update counts [language-code document-id headword sequence-number]
                        (fnil + 0.0) weight))
              counts
              (keys lemma-groups)))))

(defn write-document-counts!
  "Upserts the accumulated document-count map into document_frequencies,
   mirroring HibernateFrequencyDAO.updateDocumentFrequencies's
   insert-or-add-to-existing-row behavior."
  [db counts]
  (doseq [[[language-code document-id headword sequence-number] freq] counts]
    (jdbc/execute! db
                   ["INSERT INTO document_frequencies
                       (language_code, document_id, headword, sequence_number, weighted_frequency)
                     VALUES (?, ?, ?, ?, ?)
                     ON CONFLICT (language_code, document_id, headword, sequence_number)
                     DO UPDATE SET weighted_frequency = weighted_frequency + excluded.weighted_frequency"
                    language-code document-id headword sequence-number freq])))
