"""Shared adb/uiautomator helpers for the per-milestone screenshot scripts.

Taps resolve against a live uiautomator dump rather than hard-coded pixel coordinates, so
these keep working when layout changes. Waits are on drawn content, never on a fixed
sleep — MainActivity withholds its first frame until settings load, so a sleep silently
captures the platform splash instead of the app.
"""

from __future__ import annotations

import re
import subprocess
import time
from pathlib import Path
from xml.etree import ElementTree

PACKAGE = "com.sidharth.lumen.debug"
ACTIVITY = f"{PACKAGE}/com.sidharth.lumen.MainActivity"


class AdbTimeout(RuntimeError):
    pass


def adb(*args: str, binary: bool = False, check: bool = True, timeout: float = 60.0):
    """Every adb call is bounded.

    `adb shell uiautomator dump` hangs indefinitely when the device's window state is
    wedged — which an emulator under memory pressure does regularly. Without a timeout the
    whole capture run stalls silently instead of failing with something actionable.
    """
    try:
        result = subprocess.run(
            ["adb", *args], capture_output=True, check=check, timeout=timeout
        )
    except subprocess.TimeoutExpired as exc:
        raise AdbTimeout(f"adb {' '.join(args)} timed out after {timeout}s") from exc
    return result.stdout if binary else result.stdout.decode("utf-8", "replace")


def dump() -> ElementTree.Element:
    for _ in range(6):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False, timeout=30)
            raw = adb("shell", "cat", "/sdcard/ui.xml", check=False, timeout=30)
        except AdbTimeout as exc:
            print(f"    (retrying: {exc})", flush=True)
            time.sleep(1.0)
            continue
        if raw.lstrip().startswith("<"):
            return ElementTree.fromstring(raw)
        time.sleep(0.5)
    raise RuntimeError("uiautomator dump never produced XML")


def center(node: ElementTree.Element) -> tuple[int, int]:
    x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", node.attrib["bounds"]))
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(root, *, text=None, desc=None, contains=None):
    for node in root.iter("node"):
        if text is not None and node.attrib.get("text") == text:
            return node
        if desc is not None and node.attrib.get("content-desc") == desc:
            return node
        if contains is not None and contains.lower() in node.attrib.get("text", "").lower():
            return node
    return None


def tap(root, *, text=None, desc=None, contains=None, settle: float = 0.6):
    node = find(root, text=text, desc=desc, contains=contains)
    if node is None:
        raise LookupError(f"no node with text={text!r} desc={desc!r} contains={contains!r}")
    x, y = center(node)
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(settle)


def wait_for(*, text=None, desc=None, contains=None, timeout: float = 25.0):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        try:
            last = dump()
        except Exception:
            time.sleep(0.5)
            continue
        if find(last, text=text, desc=desc, contains=contains) is not None:
            return last
        time.sleep(0.4)
    raise TimeoutError(f"text={text!r} desc={desc!r} contains={contains!r} never appeared")


def shoot(out_dir: Path, name: str):
    png = adb("exec-out", "screencap", "-p", binary=True)
    path = out_dir / f"{name}.png"
    path.write_bytes(png)
    print(f"  wrote {path.name} ({len(png) // 1024} KB)", flush=True)


def set_system_dark(enabled: bool):
    adb("shell", "cmd", "uimode", "night", "yes" if enabled else "no")
    time.sleep(1.5)


def launch(fresh: bool = False):
    adb("shell", "am", "force-stop", PACKAGE)
    if fresh:
        adb("shell", "pm", "clear", PACKAGE)
    time.sleep(0.5)
    adb("shell", "am", "start", "-n", ACTIVITY)
    wait_for(text="Library", timeout=30)


def kill_and_relaunch():
    """`am kill` only reaps a backgrounded process, so send the app home first."""
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(1.0)
    adb("shell", "am", "kill", PACKAGE)
    time.sleep(1.0)
    adb("shell", "am", "start", "-n", ACTIVITY)
    wait_for(text="Library", timeout=30)


def import_via_saf(filename: str, expect_contains: str, timeout: float = 90.0):
    """Tap the import FAB and drive the system file picker to `filename`.

    The picker is a different app, so everything here is matched on visible text — there
    are no test tags to lean on.
    """
    tap(dump(), desc="Import a book", settle=2.0)

    root = dump()
    if find(root, text=filename) is None:
        # The picker opens on "Recent", which won't list a freshly pushed file.
        for opener in ({"desc": "Show roots"}, {"desc": "Show list"}, {"text": "Downloads"}):
            try:
                tap(dump(), settle=1.2, **opener)
            except LookupError:
                continue
        try:
            tap(dump(), text="Downloads", settle=1.5)
        except LookupError:
            pass

    wait_for(text=filename, timeout=20)
    tap(dump(), text=filename, settle=1.5)
    return wait_for(contains=expect_contains, timeout=timeout)
