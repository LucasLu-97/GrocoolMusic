#!/usr/bin/env python3
"""M0 protocol smoke test — real Kugou gateway acceptance check.

Independently reproduces KuGouMusicApi's request construction using only the
Python standard library, then calls the real gateway. Deliberately written from
`util/request.js` + `util/helper.js` instead of sharing code with the Kotlin
module, so a mismatch between the two implementations is detectable.

Usage:
    python tools/smoke_test.py                         # lite, search + stream, expect a playable URL
    python tools/smoke_test.py --keyword 儿歌
    python tools/smoke_test.py --privilege-filter 1    # force free (Privilege=0) tracks
    python tools/smoke_test.py --keyword 周杰伦 --skip-signature   # expected to FAIL (err signature)
    python tools/smoke_test.py --platform standard --no-stream

Exit code 0 = M0 acceptance passed (playable URL obtained, or search-only ok).
Exit code 1 = signature rejected / network unreachable / no playable URL.

Note: VIP tracks (Privilege=10) cannot be streamed without login + the
concept-edition free-listening Auth chain (M3/M4). This script therefore
prefers a non-VIP track, and otherwise requests the free preview part.
"""
import argparse
import hashlib
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://gateway.kugou.com"
USER_AGENT = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi"
MID = hashlib.md5(b"grocool-m0-smoke-device").hexdigest()

PLATFORMS = {
    "lite": {
        "appid": 3116,
        "clientver": 11440,
        "salt": "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA",
        "key_salt": "185672dd44712f60bb1736df5a377e82",
    },
    "standard": {
        "appid": 1005,
        "clientver": 20489,
        "salt": "OIlwieks28dk2k092lksi2UIkp",
        "key_salt": "57ae12eb6890223e355ccfcb74edf70d",
    },
}


def md5(text: str) -> str:
    return hashlib.md5(text.encode("utf-8")).hexdigest()


def render(value) -> str:
    if value is None:
        return "null"
    if isinstance(value, (dict, list)):
        return json.dumps(value, separators=(",", ":"), ensure_ascii=False)
    return str(value)


def signature_android(params: dict, salt: str) -> str:
    joined = "".join(f"{k}={render(v)}" for k, v in sorted(params.items()))
    return md5(salt + joined + salt)


def sign_key(params: dict, cfg: dict) -> str:
    return md5(
        str(params.get("hash", ""))
        + cfg["key_salt"]
        + str(params["appid"])
        + str(params["mid"])
        + "0"
    )


def call(
    path: str,
    params: dict,
    platform: str,
    extra_headers: dict,
    *,
    encrypt_key: bool = False,
    skip_signature: bool = False,
):
    cfg = PLATFORMS[platform]
    defaults = {
        "dfid": "-",
        "mid": MID,
        "uuid": "-",
        "appid": cfg["appid"],
        "clientver": cfg["clientver"],
        "clienttime": int(time.time()),
    }
    merged = dict(defaults)
    merged.update(params)

    if encrypt_key:
        merged["key"] = sign_key(merged, cfg)
    if not skip_signature:
        merged["signature"] = signature_android(merged, cfg["salt"])

    headers = {
        "User-Agent": USER_AGENT,
        "dfid": "-",
        "clienttime": str(merged["clienttime"]),
        "mid": MID,
        "kg-rc": "1",
        "kg-thash": "5d816a0",
        "kg-rec": "1",
        "kg-rf": "B9EDA08A64250DEFFBCADDEE00F8F25F",
    }
    headers.update(extra_headers)

    url = BASE + path + "?" + urllib.parse.urlencode(merged)
    request = urllib.request.Request(url, headers=headers, method="GET")

    print(f"  GET {path} | {len(merged)} params | signature {merged.get('signature', '(skipped)')[:12]} | key {merged.get('key', '(none)')[:12]}")

    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            body = response.read().decode("utf-8", "replace")
            return response.status, body, response.headers.get("ssa-code")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", "replace"), error.headers.get("ssa-code")
    except Exception as error:  # network / proxy / TLS
        return -1, f"{type(error).__name__}: {error}", None


def summarize(status: int, body: str, ssa: str | None) -> dict | None:
    print(f"  HTTP {status} | ssa-code: {ssa or '-'} | bytes: {len(body)}")
    print(f"  body: {body[:320]}")
    try:
        data = json.loads(body)
    except json.JSONDecodeError:
        print("  -> response is not JSON")
        return None
    if isinstance(data, dict):
        print(
            "  -> status=%s error_code=%s msg=%s"
            % (
                data.get("status"),
                data.get("error_code"),
                str(data.get("err_msg") or data.get("msg") or "")[:100],
            )
        )
    return data if isinstance(data, dict) else None


def pick_track(search_data: dict):
    """Prefer a non-VIP track; otherwise fall back to a VIP track's free preview."""
    try:
        lists = search_data["data"]["lists"]
    except (KeyError, TypeError):
        return None, 0, "none"

    def first_hash(item):
        for field in ("FileHash", "HQFileHash", "SQFileHash", "hash"):
            value = item.get(field)
            if value and len(str(value)) >= 16:
                return str(value).lower()
        return None

    for item in lists:
        if str(item.get("Privilege", "10")) != "10":
            hash_value = first_hash(item)
            if hash_value:
                return hash_value, 0, f"free (Privilege={item.get('Privilege')})"
    for item in lists:
        hash_value = first_hash(item)
        if hash_value:
            return hash_value, 1, "VIP free preview (IsFreePart=1)"
    return None, 0, "none"


def playable_url(stream_data: dict | None) -> str | None:
    if not stream_data:
        return None
    raw = stream_data.get("url")
    if isinstance(raw, list):
        raw = raw[0] if raw else None
    return raw if isinstance(raw, str) and raw.startswith("http") else None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--keyword", default="儿歌")
    parser.add_argument("--platform", choices=list(PLATFORMS), default="lite")
    parser.add_argument("--privilege-filter", type=int, default=0, help="1 = only free tracks")
    parser.add_argument("--no-stream", action="store_true")
    parser.add_argument("--skip-signature", action="store_true", help="negative control: MUST fail")
    args = parser.parse_args()

    print(f"=== M0 smoke test | platform={args.platform} keyword={args.keyword} ===")

    search_params = {
        "keyword": args.keyword,
        "page": 1,
        "pagesize": 30,
        "platform": "AndroidFilter",
        "iscorrection": 1,
        "privilegefilter": args.privilege_filter,
        "area_code": 1,
        "dopicfull": 1,
    }
    if args.platform == "lite":
        search_params["clientver"] = 201  # concept edition override

    print("[1] search /v2/search/song")
    status, body, ssa = call(
        "/v2/search/song",
        search_params,
        args.platform,
        {"x-router": "complexsearch.kugou.com"},
        skip_signature=args.skip_signature,
    )
    search_data = summarize(status, body, ssa)
    if status != 200 or not search_data:
        print("RESULT: FAIL — gateway unreachable or non-JSON response")
        return 1

    accepted = search_data.get("status") == 1 and search_data.get("error_code") in (0, None)
    print(f"  -> signature accepted: {accepted}")
    if not accepted:
        print("RESULT: FAIL — signature rejected")
        return 1

    if args.no_stream:
        print("RESULT: PASS (search-only)")
        return 0

    song_hash, free_part, label = pick_track(search_data)
    if not song_hash:
        print("RESULT: FAIL — no song hash in search response")
        return 1
    print(f"  -> candidate: {label} | hash {song_hash[:12]}...")

    is_lite = args.platform == "lite"
    stream_params = {
        "album_id": 0,
        "area_code": 1,
        "hash": song_hash,
        "ssa_flag": "is_fromtrack",
        "version": 11430,
        "page_id": 967177915 if is_lite else 151369488,
        "quality": "128",
        "album_audio_id": 0,
        "behavior": "play",
        "pid": 411 if is_lite else 2,
        "cmd": 26,
        "pidversion": 3001,
        "IsFreePart": free_part,
        "ppage_id": "356753938,823673182,967485191" if is_lite else "463467626,350369493,788954147",
        "cdnBackup": 1,
        "module": "",
        "clientver": 11430,
    }
    print("[2] stream /v5/url")
    status2, body2, ssa2 = call(
        "/v5/url",
        stream_params,
        args.platform,
        {"x-router": "trackercdn.kugou.com"},
        encrypt_key=True,
        skip_signature=args.skip_signature,
    )
    stream_data = summarize(status2, body2, ssa2)
    url = playable_url(stream_data)
    if url:
        host = urllib.parse.urlparse(url).netloc
        print(f"  -> PLAYABLE URL FOUND | host={host} | bitRate={stream_data.get('bitRate')} | timeLength={stream_data.get('timeLength')}")
        print("RESULT: PASS — M0 protocol acceptance criteria met")
        return 0

    print("  -> no playable URL (VIP restriction or Auth required)")
    print("RESULT: FAIL — no playable URL")
    return 1


if __name__ == "__main__":
    sys.exit(main())
