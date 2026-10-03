"""Open a conversation with execution panels, then run with --serial DEVICE.

Checks the real chronological LazyColumn: expanding and collapsing must retain the
clicked header's screen position. Uses adb on PATH or --adb PATH.
"""
import argparse
import re
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", required=True)
parser.add_argument("--adb", default="adb")
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
    visible = [n for n in nodes() if n.get("content-desc", "") == "执行过程"]
    panels = [n for n in visible if height * .15 < bounds(n)[1] < height * .65]
    if panels:
        break
    start, end = height // 5, height * 4 // 5
    if args.toward_newer:
        start, end = end, start
    if visible:
        start = height // 2
        end = start + (height // 6 if bounds(visible[0])[1] < height * .15 else -height // 6)
    adb("shell", "input", "swipe", str(width // 2), str(start),
        str(width // 2), str(end), "180")
else:
    raise AssertionError("No visible execution panel; open the test conversation first")

panel = panels[0]
before = bounds(panel)[1]
for expanded in (True, False, True, False):
    tap(panel)
    matches = [n for n in nodes() if n.get("content-desc", "") == "执行过程"]
    assert matches, "Clicked header disappeared"
    panel = min(matches, key=lambda n: abs(bounds(n)[1] - before))
    current = bounds(panel)[1]
    print(f"expanded={expanded}, header_y={current}, delta={current-before}")
    assert abs(current - before) <= 3, "Panel pushed its header upward/downward"
print("PASS: repeated expansion/collapse preserves the header position")
