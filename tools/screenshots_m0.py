#!/usr/bin/env python3
"""M0 evidence: every theme option, on both screens, surviving a process kill.

Usage:  python3 tools/screenshots_m0.py docs/screenshots/m0
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from adb_ui import (  # noqa: E402
    adb,
    dump,
    kill_and_relaunch,
    launch,
    set_system_dark,
    shoot,
    tap,
    wait_for,
)

THEMES = [
    ("Follow system", "system"),
    ("Light", "light"),
    ("Dark", "dark"),
    ("AMOLED black", "amoled"),
    ("Sepia", "sepia"),
]


def main() -> int:
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "docs/screenshots/m0")
    out.mkdir(parents=True, exist_ok=True)

    set_system_dark(False)
    launch()
    shoot(out, "01-library-system-light")

    tap(dump(), desc="Open settings")
    shoot(out, "02-settings-system-light")

    for label, slug in THEMES:
        tap(dump(), text=label)
        shoot(out, f"03-settings-{slug}")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        wait_for(text="Library")
        shoot(out, f"04-library-{slug}")
        tap(dump(), desc="Open settings")

    # Prove the choice is read back from disk, not held in memory.
    tap(dump(), text="AMOLED black")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    wait_for(text="Library")
    kill_and_relaunch()
    shoot(out, "05-library-amoled-after-process-kill")

    # And that "follow system" actually follows it.
    tap(dump(), desc="Open settings")
    tap(dump(), text="Follow system")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    wait_for(text="Library")
    set_system_dark(True)
    wait_for(text="Library")
    shoot(out, "06-library-follow-system-dark")
    set_system_dark(False)

    print("done")
    return 0


if __name__ == "__main__":
    sys.exit(main())
