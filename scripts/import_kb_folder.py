from __future__ import annotations

import argparse
import time
from pathlib import Path

import requests


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Import all .docx files under a folder into MediRAG.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="Admin@123456")
    parser.add_argument("--folder", required=True, help="Folder containing docx files")
    parser.add_argument("--category", default="authoritative")
    parser.add_argument("--description", default="权威知识库导入")
    parser.add_argument("--timeout", type=int, default=900)
    return parser.parse_args()


def login(session: requests.Session, base_url: str, username: str, password: str) -> None:
    response = session.post(
        f"{base_url}/api/user/login",
        json={"username": username, "password": password},
        timeout=30
    )
    response.raise_for_status()
    payload = response.json()
    if payload.get("code") != 200:
        raise RuntimeError(f"Login failed: {payload}")
    token = payload["data"]["token"]
    session.headers.update({"Authorization": f"Bearer {token}"})


def list_all(session: requests.Session, base_url: str) -> dict[str, dict]:
    page = 1
    existing: dict[str, dict] = {}
    while True:
        resp = session.get(
            f"{base_url}/api/knowledge/list",
            params={"current": page, "size": 100},
            timeout=30
        )
        resp.raise_for_status()
        payload = resp.json()
        if payload.get("code") != 200:
            raise RuntimeError(f"List failed: {payload}")
        records = payload["data"].get("records", [])
        for row in records:
            existing[row["name"]] = row
        if len(records) < 100:
            break
        page += 1
    return existing


def upload_docx(session: requests.Session, base_url: str, path: Path, category: str, description: str) -> None:
    with path.open("rb") as fh:
        resp = session.post(
            f"{base_url}/api/knowledge/upload",
            files={"file": (path.name, fh, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")},
            data={"category": category, "description": description},
            timeout=240
        )
    resp.raise_for_status()
    payload = resp.json()
    if payload.get("code") != 200:
        raise RuntimeError(f"Upload failed for {path.name}: {payload}")


def wait_ready(session: requests.Session, base_url: str, names: set[str], timeout_sec: int) -> None:
    deadline = time.time() + timeout_sec
    while time.time() < deadline:
        status_map = {n: list_all(session, base_url).get(n, {}).get("status") for n in names}
        if all(v == "ready" for v in status_map.values()):
            return
        if any(v == "failed" for v in status_map.values()):
            raise RuntimeError(f"Some docs failed: {status_map}")
        time.sleep(5)
    raise TimeoutError(f"Timed out waiting ready: {sorted(names)}")


def main() -> None:
    args = parse_args()
    folder = Path(args.folder)
    if not folder.exists():
        raise FileNotFoundError(folder)

    files = sorted(folder.glob("*.docx"))
    if not files:
        raise RuntimeError(f"No docx found under {folder}")

    session = requests.Session()
    login(session, args.base_url, args.username, args.password)
    existing = list_all(session, args.base_url)

    target_names: set[str] = set()
    for path in files:
        target_names.add(path.name)
        if path.name in existing:
            print(f"Skip existing: {path.name}")
            continue
        print(f"Upload: {path.name}")
        upload_docx(session, args.base_url, path, args.category, args.description)

    wait_ready(session, args.base_url, target_names, args.timeout)
    print("All documents ready.")


if __name__ == "__main__":
    main()
