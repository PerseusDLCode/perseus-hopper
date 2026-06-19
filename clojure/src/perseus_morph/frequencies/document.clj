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
   perseus-morph.walker/document-id.

   Rows reference the lemma by lemma_id rather than repeating its
   headword/sequence_number/language_code natural key: that triple is
   already the lemma's identity in perseus-morph.loader.schema's `lemmas`
   table, so duplicating it here would just be another copy to keep in
   sync (and language_code doubly so, since it's already fixed by the
   referenced lemma)."
  (:require [next.jdbc :as jdbc]))

(def ddl
  ["CREATE TABLE IF NOT EXISTS document_frequencies (
      document_id TEXT NOT NULL,
      -- ON DELETE CASCADE: this row's count is meaningless once its lemma
      -- is gone (e.g. a re-load of the lemma's language via
      -- perseus-morph.loader.core/delete-by-language!, which mints new
      -- lemma_ids), so it shouldn't outlive it either.
      lemma_id INTEGER NOT NULL REFERENCES lemmas (id) ON DELETE CASCADE,
      weighted_frequency REAL NOT NULL DEFAULT 0,
      UNIQUE (document_id, lemma_id)
    )"])

(defn init-db!
  "Creates the document_frequencies table if it doesn't already exist.
   Additive: does not touch any other table (but does assume `lemmas` from
   perseus-morph.loader.schema already exists, since lemma_id references
   it)."
  [db]
  (doseq [stmt ddl]
    (jdbc/execute! db [stmt])))

(defn update-document-counts
  "Given one token's candidate parses grouped by lemma (as
   perseus-morph.walker.parses/get-parses returns, {lemma-id [parse-row
   ...]}), accumulates `1 / (count lemma-groups)` into `counts` (a map of
   {[document-id lemma-id] count}) for every candidate lemma -- mirroring
   WordFrequencyLoader's LEMMA TokenStrategy, where each of a token's
   Lemmatizer.getLemmas results gets `tuple.count(lemmas.size())`.

   Unlike perseus-morph.frequencies.aggregator/update-morph-counts, this
   weights by the number of *distinct candidate lemmas*, not parses: a token
   with two parses that share one lemma counts as unambiguous here."
  [counts document-id lemma-groups]
  (if (empty? lemma-groups)
    counts
    (let [weight (/ 1.0 (count lemma-groups))]
      (reduce (fn [counts lemma-id]
                (update counts [document-id lemma-id] (fnil + 0.0) weight))
              counts
              (keys lemma-groups)))))

(defn write-document-counts!
  "Upserts the accumulated document-count map into document_frequencies,
   mirroring HibernateFrequencyDAO.updateDocumentFrequencies's
   insert-or-add-to-existing-row behavior."
  [db counts]
  (doseq [[[document-id lemma-id] freq] counts]
    (jdbc/execute! db
                   ["INSERT INTO document_frequencies
                       (document_id, lemma_id, weighted_frequency)
                     VALUES (?, ?, ?)
                     ON CONFLICT (document_id, lemma_id)
                     DO UPDATE SET weighted_frequency = weighted_frequency + excluded.weighted_frequency"
                    document-id lemma-id freq])))
