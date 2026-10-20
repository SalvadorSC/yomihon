#!/usr/bin/env python3
"""Build data/src/main/assets/zh/cedict_primary.tsv from a CC-CEDICT text file.

The text file is CC-BY-SA 4.0 (https://www.mdbg.net/chinese/dictionary?page=cc-cedict).
The output keeps that license. It stores each simplified headword once, with the
first reading converted to tone marks. Later readings of the same headword are dropped
so 好 stays hǎo and 的 stays de. A traditional spelling that is not itself a
simplified headword is stored with that same first reading, so 繁華 matches 繁华.
"""

import argparse
import os
import re

VOWELS = {
    "a": "āáǎà",
    "e": "ēéěè",
    "i": "īíǐì",
    "o": "ōóǒò",
    "u": "ūúǔù",
    "ü": "ǖǘǚǜ",
}
HANZI = re.compile(r"^[\u3400-\u4dbf\u4e00-\u9fff]+$")


def apply_tone(body: str, tone: int) -> str:
    chars = list(body)
    vowel_idx = [i for i, char in enumerate(chars) if char in VOWELS]
    if not vowel_idx or tone == 5:
        return body
    letters = "".join(chars[i] for i in vowel_idx)
    if "a" in letters:
        at = vowel_idx[letters.index("a")]
    elif "e" in letters:
        at = vowel_idx[letters.index("e")]
    elif "ou" in body:
        at = body.index("o")
    else:
        at = vowel_idx[-1]
    chars[at] = VOWELS[chars[at]][tone - 1]
    return "".join(chars)


def convert_syllable(syllable: str) -> str:
    syllable = syllable.replace("u:", "ü").lower()
    match = re.fullmatch(r"([a-zü]+)([1-5])", syllable)
    if not match:
        return syllable
    body, tone = match.group(1), int(match.group(2))
    if tone == 5:
        return body
    return apply_tone(body, tone)


def convert_reading(raw: str) -> str:
    return "".join(convert_syllable(part) for part in raw.split())


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("cedict", help="path to cedict_ts.u8")
    parser.add_argument(
        "-o",
        default="data/src/main/assets/zh/cedict_primary.tsv",
        help="output TSV",
    )
    args = parser.parse_args()
    readings = {}
    order = []
    traditional_aliases = {}
    with open(args.cedict, encoding="utf-8") as handle:
        for line in handle:
            if not line or line[0] in "#!":
                continue
            fields = line.split(" ", 2)
            if len(fields) < 2:
                continue
            traditional, simplified = fields[0], fields[1]
            if simplified in readings or not HANZI.match(simplified):
                continue
            start = line.find("[")
            end = line.find("]", start + 1)
            if start < 0 or end < 0:
                continue
            pinyin = convert_reading(line[start + 1 : end])
            if not pinyin:
                continue
            readings[simplified] = pinyin
            order.append(simplified)
            if (
                traditional != simplified
                and HANZI.match(traditional)
                and traditional not in traditional_aliases
            ):
                traditional_aliases[traditional] = pinyin
    for traditional, pinyin in traditional_aliases.items():
        if traditional in readings:
            continue
        readings[traditional] = pinyin
        order.append(traditional)
    os.makedirs(os.path.dirname(args.o), exist_ok=True)
    with open(args.o, "w", encoding="utf-8") as handle:
        handle.write("# CC-CEDICT primary readings for on-device segmentation.\n")
        handle.write("# Source: CC-CEDICT (MDBG), Creative Commons Attribution-ShareAlike 4.0\n")
        handle.write("# https://creativecommons.org/licenses/by-sa/4.0/\n")
        handle.write("# https://www.mdbg.net/chinese/dictionary?page=cc-cedict\n")
        handle.write("# One headword and its first CC-CEDICT reading. Later readings are omitted.\n")
        handle.write("# Simplified headwords win. Traditional spellings are added only when they differ.\n")
        for word in order:
            handle.write(f"{word}\t{readings[word]}\n")
    print(f"wrote {len(order)} entries to {args.o}")


if __name__ == "__main__":
    main()
