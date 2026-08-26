#!/usr/bin/env python3
"""Assemble the static files that Vercel serves for the browser edition."""
from __future__ import annotations

import json
import os
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"

STATIC_FILES = [
    "index.html",
    "browser.css",
    "browser.js",
    "ConceptLabLogo.svg",
    "ConceptLab-browser.jar",
]


def main() -> None:
    if DIST.exists():
        shutil.rmtree(DIST)
    DIST.mkdir(parents=True)

    for relative in STATIC_FILES:
        source = ROOT / relative
        if not source.is_file():
            raise FileNotFoundError(f"Required browser asset is missing: {relative}")
        shutil.copy2(source, DIST / source.name)

    demo_source = ROOT / "demo"
    if not demo_source.is_dir():
        raise FileNotFoundError("Required demo directory is missing")
    shutil.copytree(demo_source, DIST / "demo")

    build_meta = {
        "gitCommit": os.environ.get("VERCEL_GIT_COMMIT_SHA", "local"),
        "gitRef": os.environ.get("VERCEL_GIT_COMMIT_REF", "local"),
    }
    (DIST / "build-meta.json").write_text(
        json.dumps(build_meta, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )

    print("Browser site assembled in dist/")


if __name__ == "__main__":
    main()
