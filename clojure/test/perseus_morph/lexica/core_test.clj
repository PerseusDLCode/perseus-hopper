(ns perseus-morph.lexica.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [perseus-morph.lexica.core :as lexica]
            [perseus-morph.lexica.schema :as schema]
            [perseus-morph.sqlite :as sqlite]))

(defn- temp-db []
  (let [file (java.io.File/createTempFile "lexica-core-test" ".db")]
    (.deleteOnExit file)
    (let [db (sqlite/datasource (.getAbsolutePath file))]
      (schema/init-db! db)
      db)))

(defn- write-temp-xml ^java.io.File [contents]
  (let [file (java.io.File/createTempFile "lexica-core-test" ".xml")]
    (.deleteOnExit file)
    (spit file contents)
    file))

(defn- query-all [db sql]
  (jdbc/execute! db [sql] {:builder-fn rs/as-unqualified-maps}))

(deftest meaning-tag-test
  (testing "lexicons known to use <g> or <l> markup get their dedicated tag"
    (is (= "g" (lexica/meaning-tag "Perseus:text:1999.04.0057")))
    (is (= "l" (lexica/meaning-tag "Perseus:text:1999.04.0059"))))

  (testing "any other lexicon id defaults to plain italics"
    (is (= "i" (lexica/meaning-tag "Perseus:text:1999.04.9999")))))

(deftest load!-test
  (let [db (temp-db)
        file (write-temp-xml
              "<entryFree key=\"mh=nis\">
                 <sense id=\"n1.1\" n=\"1\" level=\"1\">wrath, <tr>anger</tr></sense>
                 <sense id=\"n1.2\" n=\"2\">grudge</sense>
                 <sense id=\"bad-id\" n=\"3\"><tr>unreadable id</tr></sense>
               </entryFree>")]

    (testing "returns a count of senses loaded"
      (is (= {:senses 3} (lexica/load! db "Perseus:text:1999.04.0057" file))))

    (testing "a well-formed id is split into entry_id/sense_id, and the lemma column
              holds SenseLoader's lexQuery (\"entry=\" + the entry's key), not the headword"
      (is (= {:entry_id 1 :sense_id 1 :document_id "Perseus:text:1999.04.0057"
              :lemma "entry=mh=nis" :sense "1" :level 1
              :short_definition "wrath, <g>anger</g>"}
             (-> (query-all db "SELECT * FROM senses WHERE sense_id = 1")
                 first
                 (dissoc :id)))))

    (testing "a sense with no level attribute defaults level to -1, matching SenseLoader"
      (is (= -1 (:level (first (query-all db "SELECT level FROM senses WHERE sense_id = 2"))))))

    (testing "a sense whose id doesn't match the entry.sense pattern gets entry_id/sense_id -1"
      (is (= {:entry_id -1 :sense_id -1}
             (-> (query-all db "SELECT entry_id, sense_id FROM senses WHERE sense = '3'")
                 first
                 (select-keys [:entry_id :sense_id])))))))

(deftest load!-truncates-long-short-definitions-test
  (let [db (temp-db)
        long-text (apply str (repeat 150 "x"))
        file (write-temp-xml
              (str "<entryFree key=\"verbosus\">"
                   "<sense id=\"n1.1\" n=\"1\"><tr>" long-text "</tr></sense>"
                   "</entryFree>"))]
    (lexica/load! db "Perseus:text:1999.04.0057" file)
    (testing "short_definition is capped at 100 characters (97 chars + an ellipsis)"
      (let [short-def (:short_definition (first (query-all db "SELECT short_definition FROM senses")))]
        (is (= 100 (count short-def)))
        (is (.endsWith short-def "..."))))))

(deftest clear-existing!-test
  (let [db (temp-db)
        file (write-temp-xml
              "<entryFree key=\"abc\"><sense id=\"n1.1\" n=\"1\">def</sense></entryFree>")]
    (lexica/load! db "lexicon-a" file)
    (lexica/load! db "lexicon-b" file)

    (testing "clearing one lexicon's senses leaves other lexicons' senses alone"
      (lexica/clear-existing! db "lexicon-a")
      (is (empty? (query-all db "SELECT * FROM senses WHERE document_id = 'lexicon-a'")))
      (is (= 1 (count (query-all db "SELECT * FROM senses WHERE document_id = 'lexicon-b'")))))))
