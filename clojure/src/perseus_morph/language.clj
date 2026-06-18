(ns perseus-morph.language
  "Per-language form normalization, ported from the relevant bits of
   perseus.language.LanguageAdapter / GreekAdapter / LatinAdapter that
   perseus.morph.ParseLoader relied on."
  (:require [clojure.string]))

(def bare-word-pattern
  "Mirrors Lemma.BARE_WORD_PATTERN: strips beta-code diacritic markers to
   produce a bare (accent-free) form."
  #"[()\\/*=|+']")

(defn bare-form [s]
  (when s
    (clojure.string/replace s bare-word-pattern "")))

(defn- uncapitalize [^String s]
  (if (and (seq s) (Character/isUpperCase (.charAt s 0)))
    (str (Character/toLowerCase (.charAt s 0)) (subs s 1))
    s))

(defn- greek-lowercase [s]
  (-> s uncapitalize (clojure.string/replace "*" "")))

(defmulti to-lowercase
  "Lowercases `s` according to the conventions of `language-code`, mirroring
   each LanguageAdapter's toLowerCase()."
  (fn [language-code _s] language-code))

(defmethod to-lowercase "greek" [_ s] (greek-lowercase s))
(defmethod to-lowercase :default [_ s] (clojure.string/lower-case s))

(defn match-case?
  "True if `language-code` is case-sensitive when matching forms. Mirrors
   LanguageAdapter#matchCase(); only Arabic overrides the default (false)."
  [language-code]
  (= language-code "arabic"))

(defn normalize-form
  "Equivalent of:
     language.getAdapter().matchCase() ? form : language.getAdapter().toLowerCase(form)"
  [language-code form]
  (if (match-case? language-code)
    form
    (to-lowercase language-code form)))

(def ^:private lemma-pattern #"^(\D+)(\d+)$")
(def ^:private hyphen-pattern #"^.+-(.+)$")

(defn parse-lemma-text
  "Splits raw <lemma> text into [headword sequence-number], mirroring
   ParseLoader.ParseHandler#createParses(). Strips '#' markers and reduces
   hyphenated forms (e.g. 'foo-bar1') to their final segment before checking
   for a trailing sequence number."
  [lemma-text]
  (let [cleaned (clojure.string/replace lemma-text "#" "")
        cleaned (if-let [[_ tail] (re-matches hyphen-pattern cleaned)]
                  tail
                  cleaned)]
    (if-let [[_ headword seq-str] (re-matches lemma-pattern cleaned)]
      [headword (Integer/parseInt seq-str)]
      [cleaned -1])))
