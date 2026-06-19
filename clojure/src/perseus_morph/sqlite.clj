(ns perseus-morph.sqlite
  "Helpers for opening morph.db. `PRAGMA foreign_keys` is a per-connection
   setting, not a persisted database property, and next.jdbc's datasources
   open a fresh connection per call rather than holding one open -- so
   turning on FK enforcement (and thus the ON DELETE CASCADE clauses in
   perseus-morph.loader.schema/perseus-morph.frequencies.document) has to
   happen via the JDBC URL itself, which org.xerial's sqlite-jdbc driver
   applies as a PRAGMA to every connection it opens from that URL."
  (:require [next.jdbc :as jdbc]))

(defn datasource
  "A next.jdbc datasource for the SQLite file at `path`, with foreign key
   enforcement (and so ON DELETE CASCADE) turned on for every connection."
  [path]
  (jdbc/get-datasource (str "jdbc:sqlite:" path "?foreign_keys=on")))
