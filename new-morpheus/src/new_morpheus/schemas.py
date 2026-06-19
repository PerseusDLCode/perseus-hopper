from pydantic import BaseModel


class ParseOut(BaseModel):
    model_config = {"from_attributes": True}

    form: str
    form_unicode: str | None
    expanded_form: str | None
    expanded_form_unicode: str | None
    part_of_speech: str | None
    person: str | None
    number: str | None
    tense: str | None
    mood: str | None
    voice: str | None
    gender: str | None
    grammatical_case: str | None
    degree: str | None
    dialect: str | None
    other: str | None
    prefix: str | None
    object: str | None
    definite: str | None
    possessive: str | None


class LemmaResult(BaseModel):
    headword: str
    sequence_number: int
    parses: list[ParseOut]
    document_frequency: float | None = None


class MorphResponse(BaseModel):
    word: str
    language_code: str
    lemmas: list[LemmaResult]
