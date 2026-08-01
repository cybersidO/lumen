#!/usr/bin/env python3
"""M1 evidence: import three real EPUBs and exercise the library surface.

Expects the three test EPUBs already pushed to /sdcard/Download:
    adb push <book>.epub /sdcard/Download/hitchhikers.epub   (etc.)

Usage:  python3 tools/screenshots_m1.py docs/screenshots/m1
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from adb_ui import (  # noqa: E402
    dump,
    import_via_saf,
    kill_and_relaunch,
    launch,
    set_system_dark,
    shoot,
    tap,
    wait_for,
)

BOOKS = [
    ("hitchhikers.epub", "Hitchhiker"),
    ("martian.epub", "Martian"),
    ("gita-essays.epub", "Gita"),
]


def main() -> int:
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "docs/screenshots/m1")
    out.mkdir(parents=True, exist_ok=True)

    set_system_dark(False)
    launch(fresh=True)
    shoot(out, "01-empty-library")

    for index, (filename, expect) in enumerate(BOOKS, start=1):
        print(f"  importing {filename}…")
        import_via_saf(filename, expect_contains=expect)
        shoot(out, f"02-after-import-{index}")

    shoot(out, "03-library-light")

    # Search
    tap(dump(), text="Search title or author")
    __import__("subprocess").run(
        ["adb", "shell", "input", "text", "martian"], check=True
    )
    wait_for(contains="Martian")
    shoot(out, "04-search-martian")
    tap(dump(), desc="Clear search", settle=1.0)

    # Sort
    tap(dump(), desc="Sort", settle=1.0)
    shoot(out, "05-sort-menu")
    tap(dump(), text="Title", settle=1.2)
    shoot(out, "06-sorted-by-title")

    # Delete prompt — long-press a cover.
    root = dump()
    node = None
    for candidate in root.iter("node"):
        if "Martian" in candidate.attrib.get("content-desc", ""):
            node = candidate
            break
    if node is not None:
        from adb_ui import adb, center
        import time

        x, y = center(node)
        adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "800")
        time.sleep(1.2)
        shoot(out, "07-delete-prompt")
        tap(dump(), text="Cancel", settle=1.0)
    else:
        print("  ! could not locate a book card to long-press")

    # Dark theme, same shelf.
    tap(dump(), desc="Open settings", settle=1.0)
    tap(dump(), text="Dark", settle=1.2)
    __import__("subprocess").run(
        ["adb", "shell", "input", "keyevent", "KEYCODE_BACK"], check=True
    )
    wait_for(text="Library")
    shoot(out, "08-library-dark")

    # Survives process death with the shelf intact.
    kill_and_relaunch()
    shoot(out, "09-library-after-process-kill")

    set_system_dark(False)
    print("done")
    return 0


if __name__ == "__main__":
    sys.exit(main())
