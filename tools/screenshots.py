#!/usr/bin/env python3
"""Drive the installed debug app over adb and capture milestone screenshots.

Taps are resolved from a live uiautomator dump rather than hard-coded pixel
coordinates, so this keeps working when layout changes.

Usage:  python3 tools/screenshots.py docs/screenshots/m0
"""

from __future__ import annotations

import re
import subprocess
import sys
import time
from pathlib import Path
from xml.etree import ElementTree

# The debug build has an applicationId suffix, but the activity class itself keeps the
# namespace package — so the component is <applicationId>/<real class>, not "/.MainActivity".
PACKAGE = "com.sidharth.lumen.debug"
ACTIVITY = f"{PACKAGE}/com.sidharth.lumen.MainActivity"


def adb(*args: str, binary: bool = False):
    result = subprocess.run(
        ["adb", *args], capture_output=True, check=True
    )
    return result.stdout if binary else result.stdout.decode("utf-8", "replace")


def dump() -> ElementTree.Element:
    """uiautomator occasionally races the animation; retry rather than fail."""
    for _ in range(5):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        raw = adb("shell", "cat", "/sdcard/ui.xml")
        if raw.lstrip().startswith("<"):
            return ElementTree.fromstring(raw)
        time.sleep(0.5)
    raise RuntimeError("uiautomator dump never produced XML")


def center(node: ElementTree.Element) -> tuple[int, int]:
    x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", node.attrib["bounds"]))
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(root: ElementTree.Element, *, text: str | None = None, desc: str | None = None):
    for node in root.iter("node"):
        if text is not None and node.attrib.get("text") == text:
            return node
        if desc is not None and node.attrib.get("content-desc") == desc:
            return node
    return None


def tap(root: ElementTree.Element, *, text: str | None = None, desc: str | None = None):
    node = find(root, text=text, desc=desc)
    if node is None:
        raise LookupError(f"no node with text={text!r} desc={desc!r}")
    x, y = center(node)
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(0.6)


def wait_for_text(*candidates: str, timeout: float = 20.0):
    """Block until one of `candidates` is on screen.

    Needed because MainActivity deliberately withholds its first frame until the
    persisted theme has been read (see DECISIONS.md), so the platform splash screen is
    still up for a beat after `am start` returns. Sleeping a fixed interval instead
    silently captures the splash.
    """
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            root = dump()
        except Exception:
            time.sleep(0.3)
            continue
        for wanted in candidates:
            if find(root, text=wanted) is not None:
                return root
        time.sleep(0.4)
    raise TimeoutError(f"none of {candidates} appeared within {timeout}s")


def shoot(out_dir: Path, name: str):
    png = adb("exec-out", "screencap", "-p", binary=True)
    path = out_dir / f"{name}.png"
    path.write_bytes(png)
    print(f"  wrote {path} ({len(png) // 1024} KB)")


def set_system_dark(enabled: bool):
    adb("shell", "cmd", "uimode", "night", "yes" if enabled else "no")
    time.sleep(1.5)


def restart_app():
    adb("shell", "am", "force-stop", PACKAGE)
    time.sleep(0.5)
    adb("shell", "am", "start", "-n", ACTIVITY)
    wait_for_text("Library", "Settings")


THEMES = [
    ("Follow system", "system"),
    ("Light", "light"),
    ("Dark", "dark"),
    ("AMOLED black", "amoled"),
    ("Sepia", "sepia"),
]


def main() -> int:
    out_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "docs/screenshots/m0")
    out_dir.mkdir(parents=True, exist_ok=True)

    set_system_dark(False)
    restart_app()
    shoot(out_dir, "01-library-system-light")

    tap(dump(), desc="Open settings")
    shoot(out_dir, "02-settings-system-light")

    for label, slug in THEMES:
        tap(dump(), text=label)
        shoot(out_dir, f"03-settings-{slug}")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        wait_for_text("No books yet")
        shoot(out_dir, f"04-library-{slug}")
        tap(dump(), desc="Open settings")

    # Prove the choice is read back from disk, not held in memory.
    tap(dump(), text="AMOLED black")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(0.5)
    adb("shell", "am", "kill", PACKAGE)
    time.sleep(1.0)
    restart_app()
    shoot(out_dir, "05-library-amoled-after-process-kill")

    # And that "follow system" actually follows it.
    tap(dump(), desc="Open settings")
    tap(dump(), text="Follow system")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    wait_for_text("No books yet")
    set_system_dark(True)
    wait_for_text("No books yet")
    shoot(out_dir, "06-library-follow-system-dark")
    set_system_dark(False)

    print("done")
    return 0


if __name__ == "__main__":
    sys.exit(main())
