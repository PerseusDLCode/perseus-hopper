(ns perseus-morph.lexica.ingest
  "CLI entry point, mirroring perseus.voting.SenseLoader's main(): loads a
   TEI lexicon XML file's senses into SQLite. Unlike SenseLoader, which
   derived the XML file path from the lexicon id via perseus.util.Config's
   document file path property, this always takes the XML file path
   explicitly -- no equivalent config layer exists on the Clojure side.
   The path may also be a directory, in which case every *.xml file in it
   (e.g. LSJ's per-letter split across grc.lsj.perseus-eng1.xml ... eng27.xml)
   is loaded as part of the same lexicon."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
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

(defn- xml-files
  "If `path` is a directory, returns its *.xml files sorted by name;
   otherwise returns `path` itself as a single-element list."
  [path]
  (let [file (io/file path)]
    (if (.isDirectory file)
      (->> (.listFiles file)
           (filter #(str/ends-with? (str/lower-case (.getName %)) ".xml"))
           (sort-by #(.getName %)))
      [file])))

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
        (let [files (xml-files filename)
              total (reduce (fn [acc file]
                              (println "Loading" (str file) "as lexicon" lexicon-id "into" (:db options))
                              (let [{:keys [senses]} (lexica/load! db lexicon-id file)]
                                (+ acc senses)))
                            0
                            files)]
          (println "Done:" {:senses total}))))))
