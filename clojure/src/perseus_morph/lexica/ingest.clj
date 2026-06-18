(ns perseus-morph.lexica.ingest
  "CLI entry point, mirroring perseus.voting.SenseLoader's main(): loads a
   TEI lexicon XML file's senses into SQLite. Unlike SenseLoader, which
   derived the XML file path from the lexicon id via perseus.util.Config's
   document file path property, this always takes the XML file path
   explicitly -- no equivalent config layer exists on the Clojure side."
  (:require [clojure.string]
            [clojure.tools.cli :as cli]
            [next.jdbc :as jdbc]
            [perseus-morph.lexica.core :as lexica]
            [perseus-morph.lexica.schema :as schema])
  (:gen-class))

(def cli-options
  [["-d" "--db PATH" "Path to the SQLite database file"
    :default "morph.db"]
   ["-n" "--no-delete" "Don't delete existing senses for this lexicon first"]
   ["-h" "--help" "Print this message"]])

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      (println summary)

      errors
      (do (println "Error(s):\n" (clojure.string/join "\n" errors))
          (System/exit 1))

      (not= (count arguments) 2)
      (do (println "Usage: clj -M -m perseus-morph.lexica.ingest [options] <lexicon id> <lexicon XML file>")
          (println summary)
          (System/exit 1))

      :else
      (let [[lexicon-id filename] arguments
            db (jdbc/get-datasource (str "jdbc:sqlite:" (:db options)))]
        (schema/init-db! db)
        (when-not (:no-delete options)
          (println "Deleting existing senses for" lexicon-id)
          (lexica/clear-existing! db lexicon-id))
        (println "Loading" filename "as lexicon" lexicon-id "into" (:db options))
        (let [result (lexica/load! db lexicon-id filename)]
          (println "Done:" result))))))
