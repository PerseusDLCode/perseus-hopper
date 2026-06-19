(ns perseus-morph.lexica.schema
  "Initializes the SQLite replacement of the MySQL `senses` table that
   perseus.voting.SenseLoader used to write to (see reading/sql/senses.sql),
   plus a new `entries` table with no MySQL predecessor (the original system
   instead read entries on demand from the source XML by byte offset; see
   perseus-morph.lexica.core).

   Despite its name, `senses.lemma` doesn't hold a headword -- it holds
   SenseLoader's lexQuery string (\"entry=\" plus the entry's `key`
   attribute), and this port preserves that wart rather than fixing it.
   `senses.definition` is *not* byte-compatible with the original
   `short_definition`, though: that column truncated anything over 100
   characters (a legacy MySQL column-width limit), which this port
   deliberately doesn't reproduce, since the point of also adding `entries`
   is to stop needing to fall back to the source XML for the full text.

   The actual DDL lives in resources/migrations (see perseus-morph.migrations),
   not here."
  (:require [perseus-morph.migrations :as migrations]))

(defn init-db!
  "Runs every pending migration against `db`, creating the senses and
   entries tables (and their indexes) if they don't already exist."
  [db]
  (migrations/migrate! db))
