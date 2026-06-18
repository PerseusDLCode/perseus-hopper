(ns perseus-morph.corpus-walker-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [perseus-morph.corpus-walker :as walker]
            [perseus-morph.frequencies.schema :as freq-schema]
            [perseus-morph.schema :as schema]))

(deftest guess-language-code-test
  (testing "canonical-greekLit/canonical-latinLit/First1KGreek filenames"
    (is (= "greek" (walker/guess-language-code "tlg0012.tlg001.perseus-grc2.xml")))
    (is (= "latin" (walker/guess-language-code "phi0119.phi017.perseus-lat2.xml")))
    (is (= "greek" (walker/guess-language-code "tlg0639.tlg001.1st1K-grc1.xml"))))

  (testing "non-Greek/Latin and non-text files are nil"
    (is (nil? (walker/guess-language-code "tlg0012.tlg001.perseus-eng3.xml")))
    (is (nil? (walker/guess-language-code "tlg0643.tlg001.1st1K-mul1.xml")))
    (is (nil? (walker/guess-language-code "__cts__.xml")))))

(defn- write-temp-xml ^java.io.File [contents]
  (let [file (java.io.File/createTempFile "corpus-walker-test" ".xml")]
    (.deleteOnExit file)
    (spit file contents)
    file))

(deftest extract-tokens-test
  (testing "tokenizes primary text, skipping <note> content"
    (let [file (write-temp-xml
                "<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">
                   <text xml:lang=\"grc\">
                     <body>
                       <div type=\"book\" n=\"1\">
                         <l n=\"1\">μῆνιν ἄειδε <note>this is an editor's note, not text</note>θεὰ</l>
                       </div>
                     </body>
                   </text>
                 </TEI>")]
      (is (= ["μῆνιν" "ἄειδε" "θεὰ"] (walker/extract-tokens file)))))

  (testing "skips speaker labels and stage directions too"
    (let [file (write-temp-xml
                "<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">
                   <text xml:lang=\"lat\">
                     <body>
                       <sp><speaker>PALAESTRA</speaker><stage>flens</stage><l>uae mihi</l></sp>
                     </body>
                   </text>
                 </TEI>")]
      (is (= ["uae" "mihi"] (walker/extract-tokens file)))))

  (testing "a note with no surrounding whitespace doesn't merge into a run-on token"
    (let [file (write-temp-xml
                "<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">
                   <text xml:lang=\"lat\"><body><l>uae<note>nota</note>mihi</l></body></text>
                 </TEI>")]
      (is (= ["uae" "mihi"] (walker/extract-tokens file))))))

(deftest process-tokens-test
  (testing "morph counts accumulate per token, prior counts as bigrams between tokens"
    (let [noun {:part_of_speech "noun"}
          verb {:part_of_speech "verb"}
          lookup {"noun-word" {[:lemma-a -1] [noun]}
                  "verb-word" {[:lemma-b -1] [verb]}}
          {:keys [morph-counts prior-counts]}
          (walker/process-tokens "greek" lookup ["noun-word" "verb-word"])]
      (is (= 1.0 (get morph-counts ["greek" noun])))
      (is (= 1.0 (get morph-counts ["greek" verb])))
      (is (= 2.0 (get morph-counts ["greek" {}])))
      (is (= 1.0 (get prior-counts ["greek" noun verb])))))

  (testing "a token absent from the dictionary contributes no morph counts and breaks the bigram chain"
    (let [noun {:part_of_speech "noun"}
          lookup {"noun-word" {[:lemma-a -1] [noun]} "unknown" {}}
          {:keys [morph-counts prior-counts]}
          (walker/process-tokens "greek" lookup ["noun-word" "unknown" "noun-word"])]
      (is (= 2.0 (get morph-counts ["greek" noun])))
      (is (empty? prior-counts)))))

(defn- temp-db []
  (let [file (java.io.File/createTempFile "corpus-walker-test" ".db")]
    (.deleteOnExit file)
    (let [db (jdbc/get-datasource (str "jdbc:sqlite:" (.getAbsolutePath file)))]
      (schema/init-db! db)
      (freq-schema/init-db! db)
      db)))

(defn- insert-parse! [db {:keys [headword language-code form part-of-speech]}]
  (jdbc/execute! db ["INSERT INTO lemmas (headword, sequence_number, language_code)
                      VALUES (?, -1, ?)" headword language-code])
  (let [lemma-id (:lemmas/id (jdbc/execute-one! db ["SELECT id FROM lemmas WHERE headword = ?" headword]))]
    (jdbc/execute! db ["INSERT INTO parses (lemma_id, language_code, form, part_of_speech, dedup_key)
                        VALUES (?, ?, ?, ?, ?)"
                        lemma-id language-code form part-of-speech part-of-speech])))

(deftest process-file!-test
  (testing "an end-to-end Greek file: tokenize, look up parses, write counts"
    (let [db (temp-db)
          file (write-temp-xml
                "<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">
                   <text xml:lang=\"grc\">
                     <body><l>μῆνιν ἄειδε</l></body>
                   </text>
                 </TEI>")
          renamed (io/file (.getParent file) "tlg0012.tlg001.perseus-grc2.xml")]
      (.renameTo file renamed)
      (.deleteOnExit renamed)
      ;; "mh=nin" / "a)/eide" are μῆνιν/ἄειδε's Beta Code forms, the same
      ;; normalization perseus-morph.loader would have stored for these
      ;; words' analyses.
      (insert-parse! db {:headword "mh=nis" :language-code "greek" :form "mh=nin" :part-of-speech "noun"})
      (insert-parse! db {:headword "a)ei/dw" :language-code "greek" :form "a)/eide" :part-of-speech "verb"})
      (let [result (walker/process-file! db renamed)]
        (is (= "greek" (:language-code result)))
        (is (= 2 (:token-count result)))
        (is (= 1.0 (:count (jdbc/execute-one! db ["SELECT count FROM morph_frequencies
                                                    WHERE part_of_speech = 'noun'"]
                                               {:builder-fn rs/as-unqualified-maps}))))
        (is (= 1.0 (:count (jdbc/execute-one! db ["SELECT count FROM prior_frequencies
                                                    WHERE previous_part_of_speech = 'noun'
                                                      AND current_part_of_speech = 'verb'"]
                                               {:builder-fn rs/as-unqualified-maps})))))))

  (testing "a non-Greek/Latin file (by filename) is skipped without writing anything"
    (let [db (temp-db)
          file (write-temp-xml "<TEI><text><body><l>hello</l></body></text></TEI>")
          renamed (io/file (.getParent file) "tlg0012.tlg001.perseus-eng3.xml")]
      (.renameTo file renamed)
      (.deleteOnExit renamed)
      (is (nil? (walker/process-file! db renamed))))))
