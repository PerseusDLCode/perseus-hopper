from sqlmodel import Session, select

from .language import bare_form, normalize_form
from .models import DocumentFrequency, Lemma, Parse, Sense

LemmaKey = tuple[str, int]

# Lexica ingested into morph.db, by language (see
# clojure/src/perseus_morph/lexica/ingest.clj). A language can have more
# than one; add to its list as more lexica are ingested (e.g. Middle
# Liddell for "grc").
LEXICA_BY_LANGUAGE = {"grc": ["lsj"], "lat": ["lewis-short"]}


def _group_by_lemma(rows: list[tuple[Parse, Lemma]]) -> dict[LemmaKey, list[Parse]]:
    grouped: dict[LemmaKey, list[Parse]] = {}
    for parse, lemma in rows:
        grouped.setdefault((lemma.headword, lemma.sequence_number), []).append(parse)
    return grouped


def _parses_matching(
    session: Session, language_code: str, column: str, value: str
) -> list[tuple[Parse, Lemma]]:
    statement = (
        select(Parse, Lemma)
        .join(Lemma, Parse.lemma_id == Lemma.id)
        .where(getattr(Parse, column) == value, Lemma.language_code == language_code)
    )
    return list(session.exec(statement))


def lookup_parses(session: Session, word: str, language_code: str) -> dict[LemmaKey, list[Parse]]:
    """Mirrors MorphController's lookup: try the word as typed (normalized
    for case), and if that finds nothing, fall back to matching bare_form
    with diacritics stripped from the original word."""
    normalized = normalize_form(language_code, word)
    rows = _parses_matching(session, language_code, "form", normalized)
    if not rows:
        rows = _parses_matching(session, language_code, "bare_form", bare_form(word))
    return _group_by_lemma(rows)


def document_frequency(
    session: Session,
    language_code: str,
    document_id: str,
    headword: str,
    sequence_number: int,
) -> float | None:
    lemma = session.exec(
        select(Lemma).where(
            Lemma.language_code == language_code,
            Lemma.headword == headword,
            Lemma.sequence_number == sequence_number,
        )
    ).first()
    if lemma is None:
        return None
    statement = select(DocumentFrequency).where(
        DocumentFrequency.document_id == document_id,
        DocumentFrequency.lemma_id == lemma.id,
    )
    row = session.exec(statement).first()
    return row.weighted_frequency if row else None


def lookup_senses(
    session: Session, language_code: str, headword: str, sequence_number: int
) -> list[Sense]:
    """Senses are keyed by "entry=" + the lexicon entry's `key` attribute,
    which equals headword + sequence_number (omitted when -1) -- see
    clojure/src/perseus_morph/lexica/core.clj. Queries every lexicon
    ingested for `language_code`, since a lemma can have entries in more
    than one (e.g. both LSJ and Middle Liddell for Greek)."""
    document_ids = LEXICA_BY_LANGUAGE.get(language_code, [])
    if not document_ids:
        return []

    key = headword if sequence_number == -1 else f"{headword}{sequence_number}"
    statement = select(Sense).where(
        Sense.document_id.in_(document_ids), Sense.lemma == f"entry={key}"
    )
    return list(session.exec(statement))
