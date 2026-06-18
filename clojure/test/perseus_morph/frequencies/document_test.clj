(ns perseus-morph.frequencies.document-test
  (:require [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [perseus-morph.frequencies.document :as doc-freq]))

(defn- temp-db []
  (let [file (java.io.File/createTempFile "document-freq-test" ".db")]
    (.deleteOnExit file)
    (let [db (jdbc/get-datasource (str "jdbc:sqlite:" (.getAbsolutePath file)))]
      (doc-freq/init-db! db)
      db)))

(defn- query-one [db sql]
  (jdbc/execute-one! db [sql] {:builder-fn rs/as-unqualified-maps}))

(deftest update-document-counts-test
  (testing "an unambiguous token (one candidate lemma) counts as a full occurrence"
    (let [lemma-groups {["facio" -1] [{:part_of_speech "verb"}]}
          counts (doc-freq/update-document-counts {} "latin" "doc-a" lemma-groups)]
      (is (= 1.0 (get counts ["latin" "doc-a" "facio" -1])))))

  (testing "an ambiguous token (two candidate lemmas) splits weight 0.5 each,
            regardless of how many parses each lemma has"
    (let [lemma-groups {["facio" -1] [{:part_of_speech "verb"}]
                        ["facies" -1] [{:part_of_speech "noun"} {:part_of_speech "noun"}]}
          counts (doc-freq/update-document-counts {} "latin" "doc-a" lemma-groups)]
      (is (= 0.5 (get counts ["latin" "doc-a" "facio" -1])))
      (is (= 0.5 (get counts ["latin" "doc-a" "facies" -1])))))

  (testing "accumulates across repeated calls (successive tokens)"
    (let [lemma-groups {["facio" -1] [{:part_of_speech "verb"}]}
          counts (-> {}
                     (doc-freq/update-document-counts "latin" "doc-a" lemma-groups)
                     (doc-freq/update-document-counts "latin" "doc-a" lemma-groups))]
      (is (= 2.0 (get counts ["latin" "doc-a" "facio" -1])))))

  (testing "a token absent from the dictionary (no candidate lemmas) is a no-op"
    (is (= {} (doc-freq/update-document-counts {} "latin" "doc-a" {})))))

(deftest write-document-counts!-test
  (let [db (temp-db)
        lemma-groups {["facio" -1] [{:part_of_speech "verb"}]}
        counts (-> {}
                   (doc-freq/update-document-counts "latin" "doc-a" lemma-groups)
                   (doc-freq/update-document-counts "latin" "doc-a" lemma-groups))]
    (doc-freq/write-document-counts! db counts)
    (testing "the lemma's weighted frequency is written, scoped to language and document"
      (is (= {:language_code "latin" :document_id "doc-a" :headword "facio"
              :sequence_number -1 :weighted_frequency 2.0}
             (query-one db "SELECT language_code, document_id, headword,
                                   sequence_number, weighted_frequency
                              FROM document_frequencies"))))
    (testing "writing again accumulates onto the existing row instead of duplicating it"
      (doc-freq/write-document-counts! db (doc-freq/update-document-counts {} "latin" "doc-a" lemma-groups))
      (is (= 3.0 (:weighted_frequency (query-one db "SELECT weighted_frequency FROM document_frequencies"))))
      (is (= 1 (:n (query-one db "SELECT COUNT(*) AS n FROM document_frequencies")))))))
