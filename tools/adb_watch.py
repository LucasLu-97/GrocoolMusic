#!/usr/bin/env python3
"""Passive ADB observer for manual real-device testing.

The human operates the phone; this script only READS state. It never taps,
swipes, wakes the screen, starts activities, installs APKs or clears logcat.

It polls `adb logcat -d` (a persistent ring buffer) and appends new lines to a
JSON result file, then takes ONE read-only uiautomator text snapshot at the end
so we can see the on-screen status such as "找到 N 首" / "正在播放：...".

Usage:
    python tools/adb_watch.py --duration 900 --result-file C:/tmp/watch.json
    # finishes early (8s grace) once a `stream status=` line appears
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
APP_TAG = "GrocoolMusic"

ADB = DEFAULT_ADB


def adb(args, timeout=60):
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


def read_logcat():
    """Read-only: app debug lines + recent error/crash lines."""
    app = adb(["logcat", "-d", "-s", APP_TAG]).stdout
    err = adb(["logcat", "-d", "-t", "200", "*:E"]).stdout
    return [l.rstrip() for l in (app + "\n" + err).splitlines() if l.strip()]


def read_ui_texts():
    """Read-only single snapshot of visible on-screen text."""
    adb(["shell", "uiautomator", "dump", "/sdcard/grocool_watch.xml"], timeout=60)
    xml = adb(["shell", "cat", "/sdcard/grocool_watch.xml"], timeout=60).stdout
    try:
        root = ET.fromstring(xml)
        return [
            n.get("text", "").strip()
            for n in root.iter("node")
            if n.get("text", "").strip()
        ]
    except ET.ParseError:
        return []


def summarize(lines, ui_texts):
    search = [l for l in lines if "search status=" in l]
    stream = [l for l in lines if "stream status=" in l]
    fatal = [
        l for l in lines
        if any(k in l for k in ("FATAL EXCEPTION", "AndroidRuntime", "Caused by:", "Process: dev.grocool"))
    ]
    status = next(
        (t for t in ui_texts if any(k in t for k in ("找到", "没有结果", "搜索失败", "正在播放", "需要 VIP", "正在获取", "搜索中", "输入关键词"))),
        "",
    )
    return {
        "search_lines": search,
        "stream_lines": stream,
        "fatal_lines": fatal[-20:],
        "ui_status": status,
        "ui_texts": ui_texts[:30],
        "verdict": (
            "PLAYBACK_OK" if any("status=1" in l for l in stream)
            else "SEARCH_OK_NO_PLAY" if any("status=1" in l for l in search)
            else "GATEWAY_ERROR" if search or stream
            else "NO_APP_LOGS"
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", default=DEFAULT_ADB)
    parser.add_argument("--duration", type=int, default=900)
    parser.add_argument("--poll", type=float, default=2.0)
    parser.add_argument("--result-file", required=True)
    parser.add_argument("--stop-on-stream", action="store_true", default=True)
    args = parser.parse_args()

    global ADB
    ADB = args.adb
    out = pathlib.Path(args.result_file)
    seen = set()
    collected = []
    deadline = time.time() + args.duration
    stream_at = None

    print(f"observing (read-only) for up to {args.duration}s; operate the phone now", flush=True)
    while time.time() < deadline:
        for line in read_logcat():
            if line not in seen:
                seen.add(line)
                collected.append(line)
                if APP_TAG in line or "FATAL" in line:
                    print("  " + line[:260], flush=True)
        # Incremental snapshot: the caller can read the result file at any time,
        # even while this observer is still running.
        out.write_text(
            json.dumps(
                {"running": True, "lines": collected[-200:], **summarize(collected, [])},
                ensure_ascii=False,
                indent=2,
            ),
            encoding="utf-8",
        )
        if "stream status=" in "\n".join(collected) and stream_at is None:
            stream_at = time.time()
            print("stream line detected, collecting 8s grace...", flush=True)
        if stream_at and time.time() - stream_at >= 8:
            break
        time.sleep(args.poll)

    ui = read_ui_texts()
    result = {
        "duration_planned": args.duration,
        "lines": collected[-200:],
        "ui_texts": ui,
        **summarize(collected, ui),
    }
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print("=== UI texts ===", flush=True)
    for t in ui[:30]:
        print("  " + t[:200], flush=True)
    print("=== VERDICT: " + result["verdict"] + " ===", flush=True)
    print("result file: " + str(out), flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
