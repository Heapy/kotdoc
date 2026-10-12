#!/usr/bin/env python3
"""Extract verbatim upstream JSON expectations. Never generate expectations from kotdoc."""
import argparse
import json
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
source = root / "upstream/pandoc-types/test/test-pandoc-types.hs"
target = root / "core/src/test/resources/pandoc-types/json-fixtures.json"
parser = argparse.ArgumentParser()
parser.add_argument("--check", action="store_true")
args = parser.parse_args()
text = source.read_text()
fixtures = []
for match in re.finditer(r"^(t_\w+) :: \((\w+), ByteString\)\n(.*?)(?=^t_\w+ ::|^-- headers)", text, re.M | re.S):
    value = re.search(r"\[s\|(.*?)\|\]", match[3], re.S)
    if not value:
        raise SystemExit(f"No literal JSON found for {match[1]}")
    json.loads(value[1])  # Validate extraction, preserving the original bytes in the string.
    fixtures.append({"name": match[1], "type": match[2], "line": text[:match.start()].count("\n") + 1, "json": value[1]})
if len(fixtures) != 56:
    raise SystemExit(f"Expected 56 upstream fixtures, found {len(fixtures)}; review upstream changes")
content = json.dumps(fixtures, ensure_ascii=False, indent=2) + "\n"
if args.check:
    if target.read_text() != content:
        raise SystemExit("Upstream JSON fixtures have drifted; run tools/import-pandoc-json-fixtures.py")
else:
    target.write_text(content)
print(f"{'Verified' if args.check else 'Imported'} {len(fixtures)} verbatim upstream JSON fixtures")
