(ns perseus-morph.language-test
  (:require [clojure.test :refer [deftest is testing]]
            [perseus-morph.language :as lang]))

(deftest bare-form-test
  (testing "strips beta-code diacritic markers"
    (is (= "ellados" (lang/bare-form "'ella/dos")))
    (is (= "enuw" (lang/bare-form "*)enuw/")))
    (is (= "h" (lang/bare-form "'h")))))

(deftest greek-lowercase-test
  (testing "uncapitalizes and strips '*' the way GreekAdapter#toLowerCase does"
    (is (= ")enuw/" (lang/to-lowercase "greek" "*)enuw/")))
    (is (= "h" (lang/to-lowercase "greek" "h")))))

(deftest latin-lowercase-test
  (testing "falls through to the default (plain) lowercase"
    (is (= "abeuntibus" (lang/to-lowercase "latin" "ABEUNTIBUS")))))

(deftest match-case-test
  (is (false? (lang/match-case? "greek")))
  (is (false? (lang/match-case? "latin")))
  (is (true? (lang/match-case? "arabic"))))

(deftest parse-lemma-text-test
  (testing "plain headword, no sequence number"
    (is (= ["facio" -1] (lang/parse-lemma-text "facio"))))
  (testing "headword with a trailing sequence number"
    (is (= ["facio" 2] (lang/parse-lemma-text "facio2"))))
  (testing "strips '#' markers"
    (is (= ["facio" 2] (lang/parse-lemma-text "fa#cio2"))))
  (testing "reduces hyphenated lemmas to their final segment"
    (is (= ["bar" 1] (lang/parse-lemma-text "foo-bar1")))))
