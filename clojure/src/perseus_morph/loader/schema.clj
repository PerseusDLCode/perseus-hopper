(ns perseus-morph.loader.schema
  "DDL for the SQLite replacement of the `hib_lemmas`/`hib_parses` MySQL
   tables that perseus.morph.ParseLoader used to write to via Hibernate."
  (:require [next.jdbc :as jdbc]
            [perseus-morph.languages.schema :as languages]))

(def ddl
  ["CREATE TABLE IF NOT EXISTS lemmas (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      headword TEXT NOT NULL,
      headword_unicode TEXT,
      bare_headword TEXT,
      sequence_number INTEGER NOT NULL DEFAULT -1,
      language_code TEXT NOT NULL REFERENCES languages (code),
      UNIQUE (headword, sequence_number, language_code)
    )"
   "CREATE INDEX IF NOT EXISTS idx_lemmas_headword
      ON lemmas (headword, language_code)"
   ;; No language_code column here: it would be a transitive duplicate of
   ;; lemmas.language_code via lemma_id (every parse's lemma already fixes
   ;; its language), so callers that need it join through lemma_id instead
   ;; (see perseus-morph.walker.parses/get-parses).
   "CREATE TABLE IF NOT EXISTS parses (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      -- ON DELETE CASCADE: a parse cannot outlive the lemma it belongs to,
      -- so perseus-morph.loader.core/delete-by-language! only has to delete
      -- lemmas directly and let this clean up their parses.
      lemma_id INTEGER NOT NULL REFERENCES lemmas (id) ON DELETE CASCADE,
      form TEXT NOT NULL,
      form_unicode TEXT,
      expanded_form TEXT,
      expanded_form_unicode TEXT,
      bare_form TEXT,
      part_of_speech TEXT,
      person TEXT,
      number TEXT,
      tense TEXT,
      mood TEXT,
      voice TEXT,
      gender TEXT,
      grammatical_case TEXT,
      degree TEXT,
      dialect TEXT,
      other TEXT,
      prefix TEXT,
      object TEXT,
      definite TEXT,
      possessive TEXT,
      -- SQLite UNIQUE treats NULL <> NULL, so a constraint across the many
      -- nullable feature columns above wouldn't dedup rows that differ only
      -- by which features are absent. dedup_key folds all of them into a
      -- single NOT NULL string so the UNIQUE constraint actually catches
      -- duplicates (see perseus-morph.loader/dedup-key).
      dedup_key TEXT NOT NULL,
      UNIQUE (lemma_id, form, dedup_key)
    )"
   "CREATE INDEX IF NOT EXISTS idx_parses_form ON parses (form)"
   "CREATE INDEX IF NOT EXISTS idx_parses_bare_form ON parses (bare_form)"
   "CREATE INDEX IF NOT EXISTS idx_parses_lemma_id ON parses (lemma_id)"])

(defn init-db!
  "Creates the languages table (lemmas.language_code references it) and the
   lemmas/parses tables (and their indexes) if they don't already exist."
  [db]
  (languages/init-db! db)
  (doseq [stmt ddl]
    (jdbc/execute! db [stmt])))
