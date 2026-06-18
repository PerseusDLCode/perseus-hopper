(ns perseus-morph.lexica.xml-parser-test
  (:require [clojure.test :refer [deftest is testing]]
            [perseus-morph.lexica.xml-parser :as xml-parser]))

(defn- write-temp-xml ^java.io.File [contents]
  (let [file (java.io.File/createTempFile "lexica-xml-parser-test" ".xml")]
    (.deleteOnExit file)
    (spit file contents)
    file))

(defn- senses-of [contents meaning-tag]
  (let [senses (atom [])]
    (xml-parser/parse-senses! (write-temp-xml contents) meaning-tag
                               (fn [sense] (swap! senses conj sense)))
    @senses))

(deftest parse-senses!-test
  (testing "a sense's attributes are captured, and tr text is wrapped in meaning-tag"
    (is (= [{:key "abc" :id "n1.1" :n "1" :level "1" :short-def "A <i>first</i> meaning."}]
           (senses-of "<entryFree key=\"abc\">
                         <sense id=\"n1.1\" n=\"1\" level=\"1\">A <tr>first</tr> meaning.</sense>
                       </entryFree>"
                      "i"))))

  (testing "gloss and hi are wrapped the same way as tr, with a configurable tag"
    (is (= [{:key "abc" :id "n1.1" :n "1" :level nil :short-def "<g>glossed</g> and <g>highlighted</g>"}]
           (senses-of "<entryFree key=\"abc\">
                         <sense id=\"n1.1\" n=\"1\"><gloss>glossed</gloss> and <hi>highlighted</hi></sense>
                       </entryFree>"
                      "g"))))

  (testing "entry/entryFree and tag names are matched case-insensitively"
    (is (= [{:key "abc" :id "n1.1" :n "1" :level nil :short-def "<i>first</i>"}]
           (senses-of "<ENTRY key=\"abc\"><SENSE id=\"n1.1\" n=\"1\"><TR>first</TR></SENSE></ENTRY>"
                      "i"))))

  (testing "a sense with no text at all gets the placeholder definition"
    (is (= [{:key "abc" :id "n1.1" :n "1" :level nil :short-def "[no specified meaning]"}]
           (senses-of "<entryFree key=\"abc\"><sense id=\"n1.1\" n=\"1\"></sense></entryFree>"
                      "i"))))

  (testing "every sense in an entry is reported, in document order, keyed to the same entry"
    (is (= [{:key "abc" :id "n1.1" :n "1" :level nil :short-def "<i>one</i>"}
            {:key "abc" :id "n1.2" :n "2" :level nil :short-def "<i>two</i>"}]
           (senses-of "<entryFree key=\"abc\">
                         <sense id=\"n1.1\" n=\"1\"><tr>one</tr></sense>
                         <sense id=\"n1.2\" n=\"2\"><tr>two</tr></sense>
                       </entryFree>"
                      "i"))))

  (testing "a later entry's key applies to its own senses, not the previous entry's"
    (is (= [{:key "abc" :id "n1.1" :n "1" :level nil :short-def "<i>one</i>"}
            {:key "xyz" :id "n2.1" :n "1" :level nil :short-def "<i>two</i>"}]
           (senses-of "<body>
                         <entryFree key=\"abc\"><sense id=\"n1.1\" n=\"1\"><tr>one</tr></sense></entryFree>
                         <entryFree key=\"xyz\"><sense id=\"n2.1\" n=\"1\"><tr>two</tr></sense></entryFree>
                       </body>"
                      "i")))))
