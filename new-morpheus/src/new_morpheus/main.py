from fastapi import Depends, FastAPI
from sqlmodel import Session

from .db import get_session
from .morph import document_frequency, lookup_parses
from .schemas import LemmaResult, MorphResponse, ParseOut

app = FastAPI(title="new-morpheus")


@app.get("/morph", response_model=MorphResponse)
def morph(
    word: str,
    language: str = "greek",
    document_id: str | None = None,
    session: Session = Depends(get_session),
) -> MorphResponse:
    grouped = lookup_parses(session, word, language)

    lemmas = [
        LemmaResult(
            headword=headword,
            sequence_number=sequence_number,
            parses=[ParseOut.model_validate(parse) for parse in parses],
            document_frequency=(
                document_frequency(session, language, document_id, headword, sequence_number)
                if document_id
                else None
            ),
        )
        for (headword, sequence_number), parses in grouped.items()
    ]

    return MorphResponse(word=word, language_code=language, lemmas=lemmas)
