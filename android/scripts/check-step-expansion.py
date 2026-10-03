"""Open a conversation with Thought/Worked panels, then run with --serial DEVICE.

Checks the real reverse LazyColumn: expanding and collapsing must retain the
clicked header's screen position. Uses adb on PATH or --adb PATH.
"""
import argparse
import re
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", required=True)
parser.add_argument("--adb", default="adb")
parser.add_argument("--kind", choices=("Thought", "Worked"), help="Only check this panel type")
parser.add_argument("--toward-newer", action="store_true", help="Search toward newer messages")
args = parser.parse_args()


def adb(*command):
    return subprocess.check_output(
        [args.adb, "-s", args.serial, *command], timeout=30
    ).decode("utf-8", errors="replace")


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/step-expansion-check.xml")
    root = ET.fromstring(adb("shell", "cat", "/sdcard/step-expansion-check.xml"))
    assert any(n.get("package") == "com.antigravity.mobile" for n in root.iter("node")), "App is not foreground"
    return list(root.iter("node"))


def bounds(node):
    return list(map(int, re.findall(r"\d+", node.get("bounds"))))


def tap(node):
    x1, y1, x2, y2 = bounds(node)
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


width, height = map(int, re.findall(r"Physical size: (\d+)x(\d+)", adb("shell", "wm", "size"))[0])
for _ in range(30):
    visible = [n for n in nodes() if n.get("text", "").startswith(args.kind or ("Thought", "Worked"))]
    panels = [n for n in visible if height * .2 < bounds(n)[1] < height * .65]
    if panels:
        break
    start, end = height // 5, height * 4 // 5
    if args.toward_newer:
        start, end = end, start
    if visible:
        start = height // 2
        end = start + (height // 6 if bounds(visible[0])[1] < height * .2 else -height // 6)
    adb("shell", "input", "swipe", str(width // 2), str(start),
        str(width // 2), str(end), "180")
else:
    raise AssertionError("No visible Thought/Worked panel; open the test conversation first")

panel = panels[0]
label = panel.get("text").rsplit("  ", 1)[0]
assert panel.get("text").endswith("›"), "Start with the panel collapsed"
before = bounds(panel)[1]
for expanded in (True, False, True, False):
    tap(panel)
    matches = [n for n in nodes() if n.get("text", "").startswith(label + "  ")]
    assert len(matches) == 1, "Clicked header disappeared or became ambiguous"
    panel = matches[0]
    assert panel.get("text").endswith("⌄" if expanded else "›"), "Panel did not toggle"
    current = bounds(panel)[1]
    print(f"expanded={expanded}, header_y={current}, delta={current-before}")
    assert abs(current - before) <= 12, "Panel pushed its header upward/downward"
print("PASS: repeated expansion/collapse preserves the header position")
