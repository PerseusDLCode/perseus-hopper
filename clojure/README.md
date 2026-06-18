# perseus-morph

A Clojure replacement for `perseus.morph.ParseLoader`: loads the
`greek.morph.xml` / `latin.morph.xml` morphology files into a **SQLite**
database instead of MySQL (via Hibernate).

## Differences from the original Java `ParseLoader`

- Lemmas are created on the fly from the `<lemma>` tags found in the morph
  XML itself, rather than matched against a pre-populated `lemmas` table
  (which in the original system came from a separate lexicon-loading step we
  don't have data for here).
- Morphological features (`pos`, `case`, `tense`, ...) are stored as plain
  columns on `parses` rather than packed into a compact per-language
  `morph_code` string (that encoding existed to save space in MySQL; it
  doesn't matter for SQLite).
- Greek forms/lemmas are additionally converted from Beta Code to precomposed
  Unicode at load time, into `form_unicode`/`expanded_form_unicode` (on
  `parses`) and `headword_unicode` (on `lemmas`), using the same UNC Epidoc
  TransCoder library (`../reading/lib/transcoder.jar`) the original Java app
  uses for Greek rendering (`perseus.document.GreekFilter`). The original
  Beta Code is still kept in `form`/`expanded_form`/`headword` (lowercased,
  for matching) and `bare_form`/`bare_headword` (diacritics stripped). Latin
  is already written in plain Latin script, so its `*_unicode` columns are
  left `NULL`.

## Usage

```sh
clj -M -m perseus-morph.core ../xml/data/greek.morph.xml
clj -M -m perseus-morph.core ../xml/data/latin.morph.xml
```

By default this writes to `./morph.db` and deletes any existing rows for the
guessed language first. Options:

```
-d, --db PATH        Path to the SQLite database file (default: morph.db)
-l, --language CODE  Language code (guessed from the filename if omitted)
-n, --no-delete       Don't delete existing parses for this language first
-h, --help
```

## Tests

```sh
clj -M:test
```
