(ns perseus-morph.walker.parses
  "Parse lookup by surface form, replacing perseus.morph.Parse.getParses
   (word, languageCode). Used by perseus-morph.walker to find every
   candidate parse of a token."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn get-parses
  "Returns every parses row whose comparable Unicode form matches `word`
   exactly for `language-code` (matched via its lemma, since parses has no
   language_code of its own -- see perseus-morph.loader.schema), grouped by
   lemma_id into {lemma-id [parse-row ...]} — mirroring the original's
   Map<Lemma, List<Parse>>, which the prior-count bigram logic and stoplist
   check need grouped per-lemma rather than flat.

   Note: matches on COALESCE(form_unicode, form), not `bare_form` -- this
   is the Unicode equivalent of what Token.getOriginalText() fed into the
   original Java lookup (there, just `form`, since the source corpus was
   Beta Code throughout). The corpus walker reads genuine Unicode TEI text,
   so the comparable key for Greek is `form_unicode`, not the still-Beta-Code
   `form` -- see perseus-morph.walker.core/token->form. But perseus-morph.loader.core
   only ever populates form_unicode for Greek (the only language morph.xml
   encodes in Beta Code); Latin/Arabic forms are already plain text in
   `form` itself, with form_unicode left NULL, so the COALESCE falls
   through to `form` for those rather than failing to match every row.
   Sufficient for this slice since no corpus walker exists yet to say
   otherwise."
  [db word language-code]
  (let [rows (jdbc/execute! db
                            ["SELECT parses.*
                                FROM parses
                                JOIN lemmas ON parses.lemma_id = lemmas.id
                               WHERE COALESCE(parses.form_unicode, parses.form) = ?
                                 AND lemmas.language_code = ?"
                             word language-code]
                            {:builder-fn rs/as-unqualified-maps})]
    (group-by :lemma_id rows)))
