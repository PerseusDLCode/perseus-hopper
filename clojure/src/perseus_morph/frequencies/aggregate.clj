(ns perseus-morph.frequencies.aggregate
  "CLI entry point, mirroring perseus.morph.MorphCodeAggregator's main(): walks
   a directory of corpus TEI files, tokenizing each Greek/Latin primary text
   and writing its morph/prior frequency counts (see
   perseus-morph.frequencies.aggregator) into the same SQLite database
   perseus-morph.loader.core populated with lemmas/parses."
  (:require [clojure.string]
            [clojure.tools.cli :as cli]
            [next.jdbc :as jdbc]
            [perseus-morph.frequencies.document :as doc-schema]
            [perseus-morph.frequencies.schema :as freq-schema]
            [perseus-morph.sqlite :as sqlite]
            [perseus-morph.walker.core :as walker])
  (:gen-class))

(def cli-options
  [["-d" "--db PATH" "Path to the SQLite database file"
    :default "morph.db"]
   ["-h" "--help" "Print this message"]])

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      (println summary)

      errors
      (do (println "Error(s):\n" (clojure.string/join "\n" errors))
          (System/exit 1))

      (not= (count arguments) 1)
      (do (println "Usage: clj -M -m perseus-morph.aggregate [options] <corpus dir>")
          (println summary)
          (System/exit 1))

      :else
      (let [dir (first arguments)
            ds (sqlite/datasource (:db options))]
        (with-open [db (jdbc/get-connection ds)]
          ;; A single connection, held open for the whole walk, with WAL +
          ;; relaxed synchronous: the default (one implicit autocommit
          ;; transaction per INSERT, journal_mode=DELETE) fsyncs on every
          ;; single upsert, which dominates runtime once write-morph-counts!/
          ;; write-prior-counts!/write-document-counts! are issuing one
          ;; statement per row per document.
          (jdbc/execute! db ["PRAGMA journal_mode=WAL"])
          (jdbc/execute! db ["PRAGMA synchronous=NORMAL"])
          (freq-schema/init-db! db)
          (doc-schema/init-db! db)
          (println "Walking" dir "into" (:db options))
          (let [result (walker/walk! db dir)]
            (println "Done:" result)))))))
