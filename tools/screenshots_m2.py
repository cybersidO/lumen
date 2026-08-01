#!/usr/bin/env python3
"""M2 evidence: a book open and readable, chrome, TOC, typography, both themes.

Assumes the library already holds the three imported EPUBs (run screenshots_m1.py first).

Usage:  python3 tools/screenshots_m2.py docs/screenshots/m2
"""

from __future__ import annotations

import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from adb_ui import adb, center, dump, find, launch, shoot, tap, wait_for  # noqa: E402

BOOK = "Martian"


def tap_book(title_fragment: str):
    root = dump()
    for node in root.iter("node"):
        if title_fragment.lower() in node.attrib.get("content-desc", "").lower():
            x, y = center(node)
            adb("shell", "input", "tap", str(x), str(y))
            time.sleep(10)
            return
    raise LookupError(f"no book card matching {title_fragment!r}")


def tap_centre():
    adb("shell", "input", "tap", "540", "1200")
    time.sleep(2)


def turn_pages(count: int):
    for _ in range(count):
        adb("shell", "input", "swipe", "900", "1200", "150", "1200", "200")
        time.sleep(2)


def main() -> int:
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "docs/screenshots/m2")
    out.mkdir(parents=True, exist_ok=True)

    launch()
    shoot(out, "01-library")

    tap_book(BOOK)
    shoot(out, "02-book-opened")

    turn_pages(4)
    shoot(out, "03-after-page-turns")

    tap_centre()
    shoot(out, "04-chrome-visible")

    # Table of contents
    try:
        tap(dump(), desc="Contents", settle=3.0)
        shoot(out, "05-table-of-contents")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(2)
    except LookupError:
        print("  ! contents button not found", flush=True)

    # Typography
    tap_centre()
    try:
        tap(dump(), desc="Text settings", settle=3.0)
        shoot(out, "06-typography-sheet")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(2)
    except LookupError:
        print("  ! text settings button not found", flush=True)

    shoot(out, "07-reader-final")
    print("done", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
