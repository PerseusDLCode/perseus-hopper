(ns perseus-morph.lexica.schema
  "DDL for the SQLite replacement of the MySQL `senses` table that
   perseus.voting.SenseLoader used to write to (see reading/sql/senses.sql).
   Despite its name, the `lemma` column doesn't hold a headword -- it holds
   SenseLoader's lexQuery string (\"entry=\" plus the entry's `key`
   attribute), and this port preserves that wart rather than fixing it, to
   stay byte-compatible with the original table's contents."
  (:require [next.jdbc :as jdbc]))

(def ddl
  ["CREATE TABLE IF NOT EXISTS senses (
      entry_id INTEGER NOT NULL DEFAULT -1,
      sense_id INTEGER NOT NULL DEFAULT -1,
      document_id TEXT NOT NULL,
      lemma TEXT NOT NULL,
      sense TEXT,
      level INTEGER,
      short_definition TEXT,
      PRIMARY KEY (entry_id, sense_id, document_id)
    )"
   "CREATE INDEX IF NOT EXISTS idx_senses_document_lemma
      ON senses (document_id, lemma)"])

(defn init-db!
  "Creates the senses table (and its index) if it doesn't already exist."
  [db]
  (doseq [stmt ddl]
    (jdbc/execute! db [stmt])))
