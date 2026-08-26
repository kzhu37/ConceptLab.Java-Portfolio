#!/usr/bin/env python3
"""Normalize JAR metadata so the committed browser artifact is reproducible."""
from __future__ import annotations

import argparse
import tempfile
import zipfile
from pathlib import Path

FIXED_TIME = (2026, 1, 1, 0, 0, 0)


def normalize(path: Path) -> None:
    with zipfile.ZipFile(path, "r") as source:
        entries = [(info.filename, source.read(info.filename)) for info in source.infolist() if not info.is_dir()]

    with tempfile.NamedTemporaryFile(prefix="conceptlab-jar-", suffix=".jar", delete=False) as tmp:
        temp_path = Path(tmp.name)

    try:
        with zipfile.ZipFile(temp_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as target:
            for name, data in sorted(entries, key=lambda item: (item[0] != "META-INF/MANIFEST.MF", item[0])):
                info = zipfile.ZipInfo(name, FIXED_TIME)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.create_system = 3
                info.external_attr = (0o644 & 0xFFFF) << 16
                target.writestr(info, data)
        temp_path.replace(path)
    finally:
        temp_path.unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("jar", nargs="?", default="ConceptLab-browser.jar")
    args = parser.parse_args()
    normalize(Path(args.jar).resolve())
