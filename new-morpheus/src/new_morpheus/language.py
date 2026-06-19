import re

# Mirrors perseus.morph.Lemma.BARE_WORD_PATTERN / perseus-morph.language/bare-word-pattern:
# strips beta-code diacritic markers to produce an accent-free form.
BARE_WORD_PATTERN = re.compile(r"[()\\/*=|+']")


def bare_form(s: str) -> str:
    return BARE_WORD_PATTERN.sub("", s)


def _greek_lowercase(s: str) -> str:
    if s and s[0].isupper():
        s = s[0].lower() + s[1:]
    return s.replace("*", "")


def to_lowercase(language_code: str, s: str) -> str:
    if language_code == "greek":
        return _greek_lowercase(s)
    return s.lower()


def match_case(language_code: str) -> bool:
    return language_code == "arabic"


def normalize_form(language_code: str, form: str) -> str:
    """Mirrors `adapter.matchCase() ? form : adapter.toLowerCase(form)`."""
    if match_case(language_code):
        return form
    return to_lowercase(language_code, form)
