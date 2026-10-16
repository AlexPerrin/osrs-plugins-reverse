#!/usr/bin/env python3
"""Device helper (holder of the device lock only): long-press the QH cyan highlight (or x y)
and pick the long-press menu option whose text contains OPTION_SUBSTRING (MenuOpened log order).
usage: ~/tools/venv/bin/python host/tools/menu_pick.py OPTION_SUBSTRING [x y]"""
import subprocess, sys, time, re, io
import numpy as np
from PIL import Image

ADB = "/mnt/c/Users/Alex/AppData/Local/Android/Sdk/platform-tools/adb.exe"
S = ["-s", "127.0.0.1:5555"]

def adb(*a, out=False):
    r = subprocess.run([ADB, *S, *a], capture_output=True)
    return r.stdout if out else None

def shot():
    return np.array(Image.open(io.BytesIO(adb("exec-out", "screencap", "-p", out=True))).convert("RGB")).astype(int)

def cyan_target(img):
    r, g, b = img[..., 0], img[..., 1], img[..., 2]
    m = (r < 80) & (g > 200) & (b > 200)
    m[:320, :] = False      # chat / QH text panel
    m[:, 1700:] = False     # side panel + minimap
    m[:, :120] = False
    ys, xs = np.nonzero(m)
    if len(xs) < 20:
        return None
    # the hull outline is below the arrow; use the lower part of the cyan pixels
    cut = np.percentile(ys, 40)
    sel = ys >= cut
    return int(np.median(xs[sel])), int(np.median(ys[sel]))

opt = sys.argv[1]
if len(sys.argv) >= 4:
    x, y = int(sys.argv[2]), int(sys.argv[3])
else:
    t = cyan_target(shot())
    if not t:
        print("no cyan highlight"); sys.exit(1)
    x, y = t
adb("logcat", "-c")
adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "900")
time.sleep(1.2)
log = adb("logcat", "-d", "-s", "osrsrl-host", out=True).decode(errors="replace")
entries = re.findall(r"MenuOpened MenuEntry\((.*?) \|", log)
if not entries:
    print("no menu at", x, y); sys.exit(1)
top_first = entries[::-1]
print("menu at", x, y, ":", top_first)
idx = next((i for i, e in enumerate(top_first) if opt.lower() in e.lower()), None)
if idx is None:
    adb("shell", "input", "keyevent", "4")  # back closes the menu? fall back to Cancel row
    cy = y + 96 + 54 * (len(top_first) - 1)
    adb("shell", "input", "tap", str(x), str(cy))
    print("option not found; cancelled"); sys.exit(2)
cy = y + 96 + 54 * idx
adb("shell", "input", "tap", str(x), str(cy))
print("tapped", top_first[idx], "at", x, cy)
