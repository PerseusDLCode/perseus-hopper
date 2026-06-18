(ns perseus-morph.core
  "CLI entry point, mirroring perseus.morph.ParseLoader's main(): loads a
   morph XML file (greek.morph.xml, latin.morph.xml, ...) into a SQLite
   database, guessing the language from the filename unless overridden."
  (:require [clojure.string]
            [clojure.tools.cli :as cli]
            [next.jdbc :as jdbc]
            [perseus-morph.loader :as loader]
            [perseus-morph.schema :as schema])
  (:gen-class))

(def cli-options
  [["-d" "--db PATH" "Path to the SQLite database file"
    :default "morph.db"]
   ["-l" "--language CODE" "Language code (guessed from the filename if omitted)"]
   ["-n" "--no-delete" "Don't delete existing parses for this language first"]
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
      (do (println "Usage: clj -M -m perseus-morph.core [options] <morph XML file>")
          (println summary)
          (System/exit 1))

      :else
      (let [filename (first arguments)
            language-code (or (:language options) (loader/guess-language-code filename))
            db (jdbc/get-datasource (str "jdbc:sqlite:" (:db options)))]
        (schema/init-db! db)
        (when-not (:no-delete options)
          (println "Deleting existing parses for" language-code)
          (loader/delete-by-language! db language-code))
        (println "Loading" filename "as language" language-code "into" (:db options))
        (let [result (loader/load! db filename language-code)]
          (println "Done:" result))))))
