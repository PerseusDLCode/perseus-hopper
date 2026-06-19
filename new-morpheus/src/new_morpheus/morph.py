from sqlmodel import Session, select

from .language import bare_form, normalize_form
from .models import DocumentFrequency, Lemma, Parse

LemmaKey = tuple[str, int]


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
        .where(getattr(Parse, column) == value, Parse.language_code == language_code)
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
    statement = select(DocumentFrequency).where(
        DocumentFrequency.language_code == language_code,
        DocumentFrequency.document_id == document_id,
        DocumentFrequency.headword == headword,
        DocumentFrequency.sequence_number == sequence_number,
    )
    row = session.exec(statement).first()
    return row.weighted_frequency if row else None
