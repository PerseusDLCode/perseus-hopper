(ns perseus-morph.frequencies.schema
  "DDL for the SQLite replacement of the MySQL morph_frequencies/
   prior_frequencies tables that perseus.morph.MorphCodeAggregator used to
   write to. The legacy tables keyed rows on a packed `morph_code` string
   (see perseus.morph.MorphCode); since the parses table abandoned that
   packing in favor of plain feature columns (see perseus-morph.loader.schema),
   these tables do the same, and reuse the NOT-NULL `feature_key` fold
   (perseus-morph.features/fold-key) for uniqueness, for the same reason
   `parses` needs `dedup_key`: SQLite's UNIQUE treats NULL <> NULL."
  (:require [clojure.string]
            [next.jdbc :as jdbc]
            [perseus-morph.features :as features]))

(defn- column-defs
  "SQL column definitions for the feature columns, optionally prefixed
   (e.g. \"previous\" -> \"previous_part_of_speech TEXT\")."
  [prefix]
  (clojure.string/join ",\n      "
                        (map #(str (when prefix (str prefix "_")) (name %) " TEXT")
                             features/feature-columns)))

(def ddl
  [(str "CREATE TABLE IF NOT EXISTS morph_frequencies (
      language_code TEXT NOT NULL,
      " (column-defs nil) ",
      feature_key TEXT NOT NULL,
      count REAL NOT NULL DEFAULT 0,
      UNIQUE (language_code, feature_key)
    )")
   "CREATE INDEX IF NOT EXISTS idx_morph_frequencies_language
      ON morph_frequencies (language_code)"
   (str "CREATE TABLE IF NOT EXISTS prior_frequencies (
      language_code TEXT NOT NULL,
      " (column-defs "previous") ",
      previous_feature_key TEXT NOT NULL,
      " (column-defs "current") ",
      current_feature_key TEXT NOT NULL,
      count REAL NOT NULL DEFAULT 0,
      UNIQUE (language_code, previous_feature_key, current_feature_key)
    )")
   "CREATE INDEX IF NOT EXISTS idx_prior_frequencies_language
      ON prior_frequencies (language_code)"])

(defn init-db!
  "Creates the morph_frequencies/prior_frequencies tables (and their
   indexes) if they don't already exist. Additive: does not touch the
   lemmas/parses tables from perseus-morph.loader.schema."
  [db]
  (doseq [stmt ddl]
    (jdbc/execute! db [stmt])))
