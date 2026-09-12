"""Batch POST to pawchive.st favorites/creator API."""
import json
import sys
import time
import urllib.request
import urllib.error

INPUT_FILE = r"C:\Users\dazo\Downloads\favorites.json"
BASE_URL = "https://pawchive.st/api/v1/favorites/creator"
COOKIE = "session=eyJfcGVybWFuZW50Ijp0cnVlLCJhY2NvdW50X2lkIjo4NjU0M30.aj0kZQ.Vvj14t6f414Wnr76v51D8uD0Viw"
DELAY = 0.15  # seconds between requests (198 * 0.15 ≈ 30s)

HEADERS = {
    "Cookie": COOKIE,
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
    "Accept": "application/json, text/plain, */*",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8,ja;q=0.7",
    "Origin": "https://pawchive.st",
    "Referer": "https://pawchive.st/",
}

def log(msg):
    print(msg, flush=True)

def load_items(path):
    with open(path, "r", encoding="utf-8-sig") as f:
        return json.load(f)

def send_post(service, item_id):
    url = f"{BASE_URL}/{service}/{item_id}"
    req = urllib.request.Request(url, method="POST")
    for k, v in HEADERS.items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")
    except Exception as e:
        return None, str(e)

def main():
    items = load_items(INPUT_FILE)
    total = len(items)
    ok = 0
    fail = 0
    log(f"Total items: {total}")
    log(f"Target: {BASE_URL}/{{service}}/{{id}}")
    log("=" * 60)

    for i, item in enumerate(items, start=1):
        service = item["service"]
        item_id = item["id"]
        name = item.get("name", "?")
        status, body = send_post(service, item_id)
        short_body = body[:120].replace("\n", " ") if body else ""
        if status and 200 <= status < 300:
            ok += 1
            log(f"[{i:>4}/{total}] OK {status} | {service}/{name} ({item_id})")
        else:
            fail += 1
            log(f"[{i:>4}/{total}] FAIL {status} | {service}/{name} ({item_id}) | {short_body}")
        time.sleep(DELAY)

    log("=" * 60)
    log(f"Done. OK: {ok}, Failed: {fail}, Total: {total}")

if __name__ == "__main__":
    main()
