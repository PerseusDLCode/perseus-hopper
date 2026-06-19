# new-morpheus

HTTP API for morphological lookups, reading from `morph.db` (the SQLite
database produced by the Clojure ingestion pipeline in `../clojure`).

## Setup

Requires the `morph.db` file to exist at `../clojure/morph.db` (run the
Clojure ingestion pipeline first if it doesn't).

```sh
uv sync
```

## Running

Dev server (autoreload, binds to `127.0.0.1`):

```sh
uv run new-morpheus-dev
```

Production server (binds to `0.0.0.0`, multiple workers):

```sh
uv run new-morpheus
```

Both respect `PORT` (default `8000`); the production server also respects
`WEB_CONCURRENCY` (default `4`) for worker count.

## Tests

```sh
uv run pytest
```

## API

`GET /morph?word=<word>&language=<language_code>&document_id=<optional>`

Returns the candidate lemmas and parses for `word`. If `document_id` is
given, each lemma includes its `document_frequency` (weighted frequency of
that lemma within the given document), or `null` if none is recorded.
