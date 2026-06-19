(ns perseus-morph.frequencies.schema
  "Initializes the SQLite replacement of the MySQL morph_frequencies/
   prior_frequencies tables that perseus.morph.MorphCodeAggregator used to
   write to. The legacy tables keyed rows on a packed `morph_code` string
   (see perseus.morph.MorphCode); since the parses table abandoned that
   packing in favor of plain feature columns (see perseus-morph.loader.schema),
   these tables do the same, and reuse the NOT-NULL `feature_key` fold
   (perseus-morph.features/fold-key) for uniqueness, for the same reason
   `parses` needs `dedup_key`: SQLite's UNIQUE treats NULL <> NULL.

   The actual DDL lives in resources/migrations (see perseus-morph.migrations),
   not here."
  (:require [perseus-morph.migrations :as migrations]))

(defn init-db!
  "Runs every pending migration against `db`, creating the languages table
   (these tables' language_code references it) and the
   morph_frequencies/prior_frequencies tables (and their indexes) if they
   don't already exist."
  [db]
  (migrations/migrate! db))
