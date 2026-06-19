(ns perseus-morph.languages.schema
  "Shared DDL for the `languages` lookup table: lemmas/parses/
   morph_frequencies/prior_frequencies all key their `language_code` column
   on it (and document_frequencies keys on `lemma_id`, which is itself
   scoped to a language via `lemmas`), so the set of valid codes lives in
   one place instead of each table trusting its own free-text column. See
   perseus-morph.language/language-name->code for the same codes' English
   names."
  (:require [next.jdbc :as jdbc]))

(def known-languages
  "[code name] pairs for every ISO 639 code this port recognizes."
  [["grc" "Greek"] ["lat" "Latin"] ["ara" "Arabic"]])

(def ddl
  ["CREATE TABLE IF NOT EXISTS languages (
      code TEXT PRIMARY KEY,
      name TEXT NOT NULL
    )"])

(defn init-db!
  "Creates the languages table if needed and seeds it with every known
   code. Idempotent, so every namespace whose tables reference
   languages(code) can safely call this from its own init-db!."
  [db]
  (doseq [stmt ddl]
    (jdbc/execute! db [stmt]))
  (doseq [[code name] known-languages]
    (jdbc/execute! db
                   ["INSERT OR IGNORE INTO languages (code, name) VALUES (?, ?)"
                    code name])))
