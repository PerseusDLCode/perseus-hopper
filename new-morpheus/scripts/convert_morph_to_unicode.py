from pathlib import Path

import beta_code
from lxml import etree

MORPH_XML = Path(__file__).parent.parent.parent / "xml" / "data" / "greek.morph.xml"
OUT = Path(__file__).parent / "greek.morph.unicode.xml"


def main():
    tree = etree.parse(MORPH_XML)

    for form in tree.iterfind(".//form"):
        form.text = beta_code.beta_code_to_greek(form.text)

    for lemma in tree.iterfind(".//lemma"):
        lemma.text = beta_code.beta_code_to_greek(lemma.text)

    with OUT.open("wb") as f:
        f.write(etree.tostring(tree, encoding="utf-8", xml_declaration=True))


if __name__ == "__main__":
    main()
