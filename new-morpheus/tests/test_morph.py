from fastapi.testclient import TestClient
from sqlmodel import Session

from new_morpheus.db import engine
from new_morpheus.main import app
from new_morpheus.morph import lookup_senses

client = TestClient(app)


def test_lookup_by_exact_form_finds_known_lemma():
    response = client.get("/morph", params={"word": "abeuntibus", "language": "lat"})

    assert response.status_code == 200
    body = response.json()
    assert body["word"] == "abeuntibus"
    assert body["language_code"] == "lat"
    assert [lemma["headword"] for lemma in body["lemmas"]] == ["abeo"]
    assert len(body["lemmas"][0]["parses"]) > 0


def test_lookup_falls_back_to_bare_form_when_accented_form_has_no_exact_match():
    # "'enuw/" is stored as the parses.form; the accent-stripped variant
    # below should only match via bare_form, mirroring MorphController's
    # second getParses() call.
    response = client.get("/morph", params={"word": "enuw/", "language": "grc"})

    assert response.status_code == 200
    body = response.json()
    assert any(lemma["headword"] == "*)enuw/" for lemma in body["lemmas"])


def test_lookup_is_case_insensitive_for_non_arabic_languages():
    lower = client.get("/morph", params={"word": "abeuntibus", "language": "lat"}).json()
    upper = client.get("/morph", params={"word": "ABEUNTIBUS", "language": "lat"}).json()

    assert [lemma["headword"] for lemma in lower["lemmas"]] == [
        lemma["headword"] for lemma in upper["lemmas"]
    ]


def test_unknown_word_returns_empty_lemma_list():
    response = client.get("/morph", params={"word": "nonexistentxyz", "language": "lat"})

    assert response.status_code == 200
    assert response.json()["lemmas"] == []


def test_document_id_attaches_weighted_frequency_for_matching_lemma():
    response = client.get(
        "/morph",
        params={
            "word": "dulce",
            "language": "lat",
            "document_id": "phi0959.phi010.perseus-lat2",
        },
    )

    assert response.status_code == 200
    lemmas = response.json()["lemmas"]
    dulcis = next(lemma for lemma in lemmas if lemma["headword"] == "dulcis")
    assert dulcis["document_frequency"] == 3.0


def test_document_frequency_is_none_when_document_id_omitted():
    response = client.get("/morph", params={"word": "dulce", "language": "lat"})

    assert response.status_code == 200
    lemmas = response.json()["lemmas"]
    assert all(lemma["document_frequency"] is None for lemma in lemmas)


def test_document_frequency_is_none_for_document_with_no_recorded_frequency():
    response = client.get(
        "/morph",
        params={
            "word": "dulce",
            "language": "lat",
            "document_id": "no-such-document",
        },
    )

    assert response.status_code == 200
    lemmas = response.json()["lemmas"]
    assert all(lemma["document_frequency"] is None for lemma in lemmas)


def test_senses_are_attached_from_the_lexicon_for_the_word_s_language():
    response = client.get("/morph", params={"word": "dulce", "language": "lat"})

    assert response.status_code == 200
    lemmas = response.json()["lemmas"]
    dulcis = next(lemma for lemma in lemmas if lemma["headword"] == "dulcis")
    assert len(dulcis["senses"]) > 0
    assert all(sense["document_id"] == "lewis-short" for sense in dulcis["senses"])


def test_lookup_senses_is_empty_for_a_language_with_no_ingested_lexicon():
    with Session(engine) as session:
        assert lookup_senses(session, "xyz", "dulcis", -1) == []
