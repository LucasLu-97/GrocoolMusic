#!/usr/bin/env python3
"""Wait for a wired ADB device, install the test APK, drive the UI and collect logs.

Designed to remove manual steps: once the phone is visible to adb, this script
installs (cover-install), launches the app, taps 搜索, taps the first free track,
and reports the gateway / player state from logcat.

Usage:
    python tools/adb_verify.py --apk C:/path/to/GrocoolMusic-0.2.1-release.apk
    python tools/adb_verify.py --apk ... --wait 900 --result-file C:/tmp/result.json
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

DEFAULT_ADB = "D:/VibeCodingSpace/tools/platform-tools/adb.exe"
PACKAGE = "dev.grocool.music"
ACTIVITY = f"{PACKAGE}/.MainActivity"
LOG_TAG = "GrocoolMusic"

ADB = DEFAULT_ADB


def adb(args, timeout=90):
    try:
        return subprocess.run(
            [ADB] + args,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
        )
    except subprocess.TimeoutExpired:
        return subprocess.CompletedProcess(args, -1, "", "timeout")


def device_lines():
    out = adb(["devices", "-l"]).stdout
    return [line for line in out.splitlines() if "\tdevice" in line or "\tunauthorized" in line or "\toffline" in line]


def wait_for_device(seconds: int):
    deadline = time.time() + seconds
    last = ""
    while time.time() < deadline:
        lines = device_lines()
        ready = [line for line in lines if "\tdevice" in line]
        if ready:
            return ready[0].split()[0]
        state = "; ".join(lines) if lines else "no device"
        if state != last:
            print(f"waiting for device: {state}", flush=True)
            last = state
        adb(["start-server"])
        time.sleep(3)
    return None


def ui_xml():
    adb(["shell", "uiautomator", "dump", "/sdcard/grocool_ui.xml"], timeout=60)
    return adb(["shell", "cat", "/sdcard/grocool_ui.xml"], timeout=60).stdout


def node_center(xml: str, predicate):
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return None
    for node in root.iter("node"):
        if predicate(node):
            match = re.findall(r"\[(\d+),(\d+)\]", node.get("bounds", ""))
            if len(match) == 2:
                x1, y1 = map(int, match[0])
                x2, y2 = map(int, match[1])
                return ((x1 + x2) // 2, (y1 + y2) // 2)
    return None


def visible_texts(xml: str):
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return []
    return [n.get("text", "").strip() for n in root.iter("node") if n.get("text", "").strip()]


def tap(point):
    adb(["shell", "input", "tap", str(point[0]), str(point[1])])


def logs():
    return adb(["logcat", "-d", "-s", LOG_TAG], timeout=60).stdout.strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--adb", default=DEFAULT_ADB)
    parser.add_argument("--wait", type=int, default=600)
    parser.add_argument("--result-file", default=None)
    args = parser.parse_args()

    global ADB
    ADB = args.adb
    apk = pathlib.Path(args.apk)
    result = {"device": None, "install": None, "search": None, "stream": None, "logs": ""}

    if not apk.exists():
        print(f"ERROR: APK not found: {apk}", file=sys.stderr)
        return 2

    serial = wait_for_device(args.wait)
    if not serial:
        print("RESULT: device never appeared; check USB mode / debug authorization")
        result["error"] = "no device"
        if args.result_file:
            pathlib.Path(args.result_file).write_text(json.dumps(result, ensure_ascii=False, indent=2))
        return 1
    result["device"] = serial
    print(f"device online: {serial}", flush=True)

    adb(["logcat", "-c"])
    install = adb(["install", "-r", str(apk)], timeout=240)
    print("install:", (install.stdout + install.stderr).strip()[-300:], flush=True)
    result["install"] = "Success" in install.stdout
    if not result["install"]:
        result["error"] = "install failed"
        if args.result_file:
            pathlib.Path(args.result_file).write_text(json.dumps(result, ensure_ascii=False, indent=2))
        return 1

    adb(["shell", "am", "force-stop", PACKAGE])
    adb(["shell", "am", "start", "-n", ACTIVITY])
    time.sleep(3)

    xml = ui_xml()
    search_button = node_center(xml, lambda n: n.get("text", "") == "搜索")
    if search_button:
        print(f"tapping 搜索 at {search_button}", flush=True)
        tap(search_button)
    else:
        print("WARN: 搜索 button not found in UI dump; texts:", visible_texts(xml)[:12], flush=True)

    time.sleep(6)
    after_search = ui_xml()
    texts = visible_texts(after_search)
    status_line = next((t for t in texts if "找到" in t or "酷狗返回" in t or "失败" in t or "没有结果" in t), "")
    result["search"] = status_line
    print("search status:", status_line or "(none)", flush=True)

    free_badge = node_center(after_search, lambda n: n.get("text", "").strip() == "免费")
    if free_badge:
        print(f"tapping first free track at {free_badge}", flush=True)
        tap(free_badge)
        time.sleep(6)

    final_logs = logs()
    result["logs"] = final_logs
    result["stream"] = next((line for line in final_logs.splitlines() if "stream status" in line), "")
    print("=== logcat ===", flush=True)
    print(final_logs[-2000:], flush=True)
    print("=== result ===", flush=True)
    print(json.dumps({k: v for k, v in result.items() if k != "logs"}, ensure_ascii=False), flush=True)

    if args.result_file:
        pathlib.Path(args.result_file).write_text(
            json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
        )
    return 0 if "找到" in status_line else 1


if __name__ == "__main__":
    sys.exit(main())
