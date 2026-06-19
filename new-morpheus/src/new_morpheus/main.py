import os

import uvicorn
from fastapi import Depends, FastAPI
from sqlmodel import Session

from .db import get_session
from .morph import document_frequency, lookup_parses, lookup_senses
from .schemas import LemmaResult, MorphResponse, ParseOut, SenseOut

app = FastAPI(title="new-morpheus")


@app.get("/morph", response_model=MorphResponse)
def morph(
    word: str,
    language: str = "grc",
    document_id: str | None = None,
    session: Session = Depends(get_session),
) -> MorphResponse:
    grouped = lookup_parses(session, word, language)

    lemmas = [
        LemmaResult(
            headword=headword,
            sequence_number=sequence_number,
            parses=[ParseOut.model_validate(parse) for parse in parses],
            senses=[
                SenseOut.model_validate(sense)
                for sense in lookup_senses(session, language, headword, sequence_number)
            ],
            document_frequency=(
                document_frequency(session, language, document_id, headword, sequence_number)
                if document_id
                else None
            ),
        )
        for (headword, sequence_number), parses in grouped.items()
    ]

    return MorphResponse(word=word, language_code=language, lemmas=lemmas)


def dev() -> None:
    """Entry point for `uv run new-morpheus-dev`: autoreloading local server."""
    uvicorn.run("new_morpheus.main:app", host="127.0.0.1", port=int(os.environ.get("PORT", 8000)), reload=True)


def serve() -> None:
    """Entry point for `uv run new-morpheus`: production server, no reload."""
    uvicorn.run(
        "new_morpheus.main:app",
        host="0.0.0.0",
        port=int(os.environ.get("PORT", 8000)),
        workers=int(os.environ.get("WEB_CONCURRENCY", 4)),
    )
