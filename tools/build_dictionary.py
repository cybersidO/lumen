#!/usr/bin/env python3
"""Build Lumen's bundled offline dictionary from WordNet 3.1 + CMUdict.

Produces a single SQLite file the app copies out of its assets on first run.
Nothing here runs on the device; this is a one-shot asset build.

    python3 tools/build_dictionary.py <wordnet-dict-dir> <cmudict.dict> <out.db>

Sources and licences (recorded in the `meta` table so the app can show them):
  * WordNet 3.1 — Princeton University. WordNet License (BSD-style, permissive).
  * CMU Pronouncing Dictionary 0.7b — Carnegie Mellon. Public domain / BSD-2.

WordNet carries no pronunciation data, which is why CMUdict is here at all: §2.4 asks
for pronunciation in the lookup sheet.
"""

from __future__ import annotations

import os
import re
import sqlite3
import sys
from collections import defaultdict

POS_FILES = {"noun": "n", "verb": "v", "adj": "a", "adv": "r"}

# ARPAbet → IPA. Approximate but consistent; a reader wants a pronunciation hint,
# not a phonetician's transcription.
ARPABET_IPA = {
    "AA": "ɑ", "AE": "æ", "AH": "ʌ", "AO": "ɔ", "AW": "aʊ", "AY": "aɪ",
    "B": "b", "CH": "tʃ", "D": "d", "DH": "ð", "EH": "ɛ", "ER": "ɝ",
    "EY": "eɪ", "F": "f", "G": "ɡ", "HH": "h", "IH": "ɪ", "IY": "i",
    "JH": "dʒ", "K": "k", "L": "l", "M": "m", "N": "n", "NG": "ŋ",
    "OW": "oʊ", "OY": "ɔɪ", "P": "p", "R": "ɹ", "S": "s", "SH": "ʃ",
    "T": "t", "TH": "θ", "UH": "ʊ", "UW": "u", "V": "v", "W": "w",
    "Y": "j", "Z": "z", "ZH": "ʒ",
}
VOWELS = {
    "AA", "AE", "AH", "AO", "AW", "AY", "EH", "ER", "EY",
    "IH", "IY", "OW", "OY", "UH", "UW",
}


def parse_data_file(path: str, pos: str) -> dict[int, tuple[str, str]]:
    """offset -> (definition, '|'-joined examples), from a WordNet data.<pos> file."""
    synsets: dict[int, tuple[str, str]] = {}
    with open(path, encoding="latin-1") as handle:
        for line in handle:
            if line.startswith("  "):  # licence header
                continue
            head, _, gloss = line.partition("|")
            if not gloss:
                continue
            try:
                offset = int(head.split(" ", 1)[0])
            except ValueError:
                continue

            gloss = gloss.strip()
            # Gloss is: definition; "example"; "example"
            examples = re.findall(r'"([^"]+)"', gloss)
            definition = re.split(r';\s*"', gloss)[0].strip().rstrip(";").strip()
            synsets[offset] = (definition, "|".join(examples))
    return synsets


def parse_index_file(path: str) -> list[tuple[str, list[int]]]:
    """(lemma, [synset offsets in sense order]) from a WordNet index.<pos> file."""
    entries: list[tuple[str, list[int]]] = []
    with open(path, encoding="latin-1") as handle:
        for line in handle:
            if line.startswith("  "):
                continue
            parts = line.split()
            if len(parts) < 6:
                continue
            lemma = parts[0]
            try:
                p_cnt = int(parts[3])
                sense_cnt = int(parts[2])
            except ValueError:
                continue
            # lemma pos synset_cnt p_cnt [ptr_symbols...] sense_cnt tagsense_cnt offsets...
            offsets_start = 4 + p_cnt + 2
            offsets = [int(x) for x in parts[offsets_start:offsets_start + sense_cnt]]
            entries.append((lemma.replace("_", " ").lower(), offsets))
    return entries


def parse_exceptions(path: str, pos: str) -> list[tuple[str, str, str]]:
    """(inflected, base, pos) — WordNet's own irregular-form lists (mice → mouse)."""
    rows: list[tuple[str, str, str]] = []
    if not os.path.exists(path):
        return rows
    with open(path, encoding="latin-1") as handle:
        for line in handle:
            parts = line.split()
            if len(parts) < 2:
                continue
            inflected = parts[0].replace("_", " ").lower()
            for base in parts[1:]:
                rows.append((inflected, base.replace("_", " ").lower(), pos))
    return rows


def arpabet_to_ipa(phones: list[str]) -> str:
    out: list[str] = []
    for phone in phones:
        stress = ""
        base = phone
        if phone and phone[-1].isdigit():
            base, digit = phone[:-1], phone[-1]
            if digit == "1":
                stress = "ˈ"
            elif digit == "2":
                stress = "ˌ"
        symbol = ARPABET_IPA.get(base)
        if symbol is None:
            continue
        # Unstressed AH is schwa; unstressed ER is the r-coloured schwa.
        if base == "AH" and not stress:
            symbol = "ə"
        elif base == "ER" and not stress:
            symbol = "ɚ"
        out.append(stress + symbol if base in VOWELS else symbol)
    return "".join(out)


def main() -> int:
    if len(sys.argv) != 4:
        print(__doc__)
        return 2
    wn_dir, cmudict_path, out_path = sys.argv[1:4]

    if os.path.exists(out_path):
        os.remove(out_path)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)

    db = sqlite3.connect(out_path)
    db.executescript(
        """
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;

        CREATE TABLE synsets (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            pos TEXT NOT NULL,
            definition TEXT NOT NULL,
            examples TEXT
        );

        -- One row per word-sense pair. Definitions live in `synsets` so a synset shared
        -- by several lemmas is stored once, which is most of the size saving.
        CREATE TABLE senses (
            lemma TEXT NOT NULL,
            pos TEXT NOT NULL,
            sense_num INTEGER NOT NULL,
            synset_id INTEGER NOT NULL
        );

        CREATE TABLE exceptions (
            inflected TEXT NOT NULL,
            base TEXT NOT NULL,
            pos TEXT NOT NULL
        );

        CREATE TABLE pronunciations (
            lemma TEXT PRIMARY KEY,
            ipa TEXT NOT NULL
        );

        CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        """
    )

    synset_ids: dict[tuple[str, int], int] = {}
    lemmas: set[str] = set()
    total_senses = 0

    for name, pos in POS_FILES.items():
        data_path = os.path.join(wn_dir, f"data.{name}")
        index_path = os.path.join(wn_dir, f"index.{name}")
        if not os.path.exists(data_path):
            print(f"  ! missing {data_path}", file=sys.stderr)
            continue

        synsets = parse_data_file(data_path, pos)
        rows = [(pos, d, e or None) for d, e in synsets.values()]
        offsets = list(synsets.keys())
        cur = db.executemany(
            "INSERT INTO synsets(pos, definition, examples) VALUES (?,?,?)", rows
        )
        # Re-read the ids we just assigned, in insertion order.
        first_id = db.execute(
            "SELECT MIN(id) FROM (SELECT id FROM synsets ORDER BY id DESC LIMIT ?)",
            (len(rows),),
        ).fetchone()[0]
        for i, offset in enumerate(offsets):
            synset_ids[(pos, offset)] = first_id + i

        sense_rows = []
        for lemma, lemma_offsets in parse_index_file(index_path):
            lemmas.add(lemma)
            for sense_num, offset in enumerate(lemma_offsets, start=1):
                sid = synset_ids.get((pos, offset))
                if sid is not None:
                    sense_rows.append((lemma, pos, sense_num, sid))
        db.executemany(
            "INSERT INTO senses(lemma, pos, sense_num, synset_id) VALUES (?,?,?,?)",
            sense_rows,
        )
        total_senses += len(sense_rows)
        print(f"  {name}: {len(rows)} synsets, {len(sense_rows)} senses")

        exc_rows = parse_exceptions(os.path.join(wn_dir, f"{name}.exc"), pos)
        db.executemany(
            "INSERT INTO exceptions(inflected, base, pos) VALUES (?,?,?)", exc_rows
        )
        print(f"  {name}: {len(exc_rows)} exceptions")

    # Pronunciations, restricted to lemmas WordNet actually knows — the rest would be
    # dead weight in the APK.
    pron_rows = []
    seen: set[str] = set()
    with open(cmudict_path, encoding="latin-1") as handle:
        for line in handle:
            line = line.split("#", 1)[0].strip()
            if not line:
                continue
            parts = line.split()
            word = parts[0].lower()
            if "(" in word:  # alternate pronunciation, keep only the first
                continue
            if word in seen or word not in lemmas:
                continue
            ipa = arpabet_to_ipa(parts[1:])
            if ipa:
                seen.add(word)
                pron_rows.append((word, ipa))
    db.executemany("INSERT INTO pronunciations(lemma, ipa) VALUES (?,?)", pron_rows)
    print(f"  pronunciations: {len(pron_rows)}")

    db.executescript(
        """
        CREATE INDEX idx_senses_lemma ON senses(lemma);
        CREATE INDEX idx_exceptions_inflected ON exceptions(inflected);
        """
    )
    db.executemany(
        "INSERT INTO meta(key, value) VALUES (?,?)",
        [
            ("schema", "1"),
            ("source", "WordNet 3.1 (Princeton) + CMUdict 0.7b (CMU)"),
            ("wordnet_licence", "WordNet 3.1 License — permissive, BSD-style"),
            ("cmudict_licence", "CMUdict — BSD-2-Clause / public domain"),
            ("senses", str(total_senses)),
            ("lemmas", str(len(lemmas))),
        ],
    )
    db.commit()
    db.execute("VACUUM")
    db.close()

    size_mb = os.path.getsize(out_path) / (1024 * 1024)
    print(f"wrote {out_path} ({size_mb:.1f} MB), {len(lemmas)} lemmas")
    return 0


if __name__ == "__main__":
    sys.exit(main())
