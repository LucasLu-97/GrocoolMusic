#!/usr/bin/env python3
"""Download a GrocoolMusic Release APK using the fastest reachable mirror.

Why: GitHub release直链在本机代理下被拦（CONNECT 502），而 api.github.com
的 asset 端点只有约 27 KB/s。实测 gh-proxy.com 可达约 1.25 MB/s，
因此先并发测速、再选最优线路，必要时复用探测时已下完的文件。

Usage:
    python tools/download_release.py                     # latest release
    python tools/download_release.py --tag v0.2.1-build.6
    python tools/download_release.py --out /path/to/app.apk --probe-seconds 10
"""
import argparse
import concurrent.futures
import hashlib
import json
import pathlib
import subprocess
import sys
import urllib.request
import zipfile

RELEASES_API = "https://api.github.com/repos/LucasLu-97/GrocoolMusic/releases"
DEFAULT_OUT_DIR = pathlib.Path("C:/Users/Lucas.Lu/.workbuddy/tmp")

MIRRORS = [
    ("direct", ""),
    ("gh-proxy", "https://gh-proxy.com/"),
    ("ghfast", "https://ghfast.top/"),
    ("ghproxy.net", "https://ghproxy.net/"),
    ("moeyy", "https://github.moeyy.xyz/"),
    ("gitmirror", "https://hub.gitmirror.com/"),
    ("llkk", "https://gh.llkk.cc/"),
    ("ghproxy.cn", "https://ghproxy.cn/"),
    ("cf.ghproxy", "https://cf.ghproxy.cc/"),
    ("ghproxy.com", "https://ghproxy.com/"),
]


def resolve_asset(tag: str | None):
    req = urllib.request.Request(
        RELEASES_API,
        headers={"Accept": "application/vnd.github+json", "User-Agent": "GrocoolMusic-Downloader"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        releases = json.load(resp)
    if not releases:
        raise RuntimeError("no releases found")
    release = next((r for r in releases if r["tag_name"] == tag), releases[0]) if tag else releases[0]
    asset = next(a for a in release["assets"] if a["name"].endswith(".apk"))
    return release["tag_name"], asset["name"], asset["size"], asset["browser_download_url"]


def probe(entry, url: str, window: int, tmpdir: pathlib.Path):
    name, prefix = entry
    target = tmpdir / (name.replace(".", "_") + ".probe")
    cmd = ["curl", "-sS", "-L", "-m", str(window), "-o", str(target), prefix + url]
    try:
        subprocess.run(cmd, capture_output=True, timeout=window + 5)
    except subprocess.TimeoutExpired:
        pass
    size = target.stat().st_size if target.exists() else 0
    valid = size > 4 and target.read_bytes()[:4] == b"PK\x03\x04"
    return {"name": name, "prefix": prefix, "bytes": size, "valid": valid, "path": target}


def validate(path: pathlib.Path, expected: int) -> bool:
    if not path.exists() or path.stat().st_size != expected:
        return False
    try:
        with zipfile.ZipFile(path) as zf:
            names = zf.namelist()
            return "AndroidManifest.xml" in names and any(
                n.startswith("classes") and n.endswith(".dex") for n in names
            )
    except zipfile.BadZipFile:
        return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", default=None)
    parser.add_argument("--out", default=None)
    parser.add_argument("--probe-seconds", type=int, default=8)
    args = parser.parse_args()

    tag, name, expected, url = resolve_asset(args.tag)
    print(f"target: {tag} / {name} ({expected} bytes)")

    tmpdir = DEFAULT_OUT_DIR / "probes"
    tmpdir.mkdir(parents=True, exist_ok=True)

    print(f"probing {len(MIRRORS)} mirrors for {args.probe_seconds}s each ...")
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(MIRRORS)) as pool:
        results = list(
            pool.map(lambda m: probe(m, url, args.probe_seconds, tmpdir), MIRRORS)
        )
    results.sort(key=lambda r: r["bytes"], reverse=True)
    for r in results:
        print(f"  {r['name']:14s} {r['bytes']:9d} bytes  valid_zip={r['valid']}  ~{r['bytes']/args.probe_seconds/1024:.0f} KB/s")

    candidates = [r for r in results if r["valid"]]
    if not candidates:
        print("ERROR: no mirror produced a valid APK", file=sys.stderr)
        return 1

    out = pathlib.Path(args.out) if args.out else DEFAULT_OUT_DIR / name
    out.parent.mkdir(parents=True, exist_ok=True)

    # A probe that already fetched the whole file is the best possible result.
    complete = next((r for r in candidates if r["bytes"] == expected), None)
    if complete:
        out.write_bytes(complete["path"].read_bytes())
        print(f"reused complete probe from {complete['name']}")
    else:
        for r in candidates:
            print(f"downloading via {r['name']} ...")
            cmd = ["curl", "-sS", "-L", "-m", "240", "--retry", "2", "-o", str(out), r["prefix"] + url]
            subprocess.run(cmd, capture_output=True, timeout=300)
            if validate(out, expected):
                break
            print(f"  {r['name']} incomplete, trying next mirror")

    if not validate(out, expected):
        print("ERROR: downloaded file failed APK validation", file=sys.stderr)
        return 1

    digest = hashlib.sha256(out.read_bytes()).hexdigest()
    print(f"OK: {out} ({out.stat().st_size} bytes)")
    print(f"sha256: {digest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
