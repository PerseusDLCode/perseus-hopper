(ns perseus-morph.walker.parses
  "Parse lookup by surface form, replacing perseus.morph.Parse.getParses
   (word, languageCode). Used by perseus-morph.walker to find every
   candidate parse of a token."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn get-parses
  "Returns every parses row (joined with its lemma's headword and
   sequence_number) whose `form` matches `word` exactly for `language-code`,
   grouped by lemma into {[headword sequence_number] [parse-row ...]} —
   mirroring the original's Map<Lemma, List<Parse>>, which the prior-count
   bigram logic and stoplist check need grouped per-lemma rather than flat.

   Note: matches on `form`, not `bare_form` — this is what
   Token.getOriginalText() fed into the original Java lookup, and is
   sufficient for this slice since no corpus walker exists yet to say
   otherwise."
  [db word language-code]
  (let [rows (jdbc/execute! db
                            ["SELECT parses.*, lemmas.headword, lemmas.sequence_number
                                FROM parses
                                JOIN lemmas ON parses.lemma_id = lemmas.id
                               WHERE parses.form = ? AND parses.language_code = ?"
                             word language-code]
                            {:builder-fn rs/as-unqualified-maps})]
    (group-by (fn [row] [(:headword row) (:sequence_number row)])
              rows)))
