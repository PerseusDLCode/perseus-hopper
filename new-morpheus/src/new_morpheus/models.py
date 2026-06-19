from sqlmodel import Field, SQLModel


class Lemma(SQLModel, table=True):
    __tablename__ = "lemmas"

    id: int | None = Field(default=None, primary_key=True)
    headword: str
    headword_unicode: str | None = None
    bare_headword: str | None = None
    sequence_number: int = -1
    language_code: str


class Parse(SQLModel, table=True):
    __tablename__ = "parses"

    id: int | None = Field(default=None, primary_key=True)
    lemma_id: int = Field(foreign_key="lemmas.id")
    language_code: str
    form: str
    form_unicode: str | None = None
    expanded_form: str | None = None
    expanded_form_unicode: str | None = None
    bare_form: str | None = None
    part_of_speech: str | None = None
    person: str | None = None
    number: str | None = None
    tense: str | None = None
    mood: str | None = None
    voice: str | None = None
    gender: str | None = None
    grammatical_case: str | None = None
    degree: str | None = None
    dialect: str | None = None
    other: str | None = None
    prefix: str | None = None
    object: str | None = None
    definite: str | None = None
    possessive: str | None = None
    dedup_key: str


class DocumentFrequency(SQLModel, table=True):
    __tablename__ = "document_frequencies"

    language_code: str = Field(primary_key=True)
    document_id: str = Field(primary_key=True)
    headword: str = Field(primary_key=True)
    sequence_number: int = Field(default=-1, primary_key=True)
    weighted_frequency: float = 0
