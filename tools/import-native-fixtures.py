#!/usr/bin/env python3
"""Generate Native reader/writer expectations using pinned Pandoc, never Kotlin.

All tracked upstream .native test fixtures are included, even those rejected by
upstream's own Native reader. Run --check-source without Pandoc to verify source
integrity in CI. Regeneration requires exactly the pinned Pandoc release.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
source = root / "upstream/pandoc"
target = root / "core/src/test/resources/native/upstream.json"
parser = argparse.ArgumentParser()
parser.add_argument("--check-source", action="store_true")
args = parser.parse_args()
paths = sorted(subprocess.check_output(["git", "-C", str(source), "ls-files", "test/*.native", "test/**/*.native"], text=True).splitlines())
if args.check_source:
    fixtures = json.loads(target.read_text())
    assert [f["path"] for f in fixtures] == paths, "Native source inventory drifted"
    for fixture in fixtures:
        assert hashlib.sha256((source / fixture["path"]).read_bytes()).hexdigest() == fixture["sha256"], fixture["path"]
    print(f"Verified {len(fixtures)} upstream Native source fixtures")
    raise SystemExit()

assert subprocess.check_output(["pandoc", "--version"], text=True).splitlines()[0] == "pandoc 3.12.1"
fixtures = []
for index, path in enumerate(paths):
    original = (source / path).read_bytes()
    read = subprocess.run(["pandoc", "-f", "native", "-t", "json"], input=original, capture_output=True)
    fixture = {"path": path, "sha256": hashlib.sha256(original).hexdigest(), "accepted": read.returncode == 0}
    if read.returncode == 0:
        fixture["json"] = json.loads(read.stdout)
        # API writer defaults are 72 columns; command line default is also 72.
        # CLI guarantees a terminal newline which writeNative itself does not add for fragments.
        for standalone, key in [(False, "fragment"), (True, "standalone")]:
            command = ["pandoc", "-f", "json", "-t", "native", "--columns=72"] + (["--standalone"] if standalone else [])
            rendered = subprocess.check_output(command, input=read.stdout).decode()
            fixture[key] = rendered if standalone else rendered.removesuffix("\n")
    else:
        fixture["error"] = read.stderr.decode().strip()
    fixtures.append(fixture)
    if (index + 1) % 50 == 0:
        print(f"Generated {index + 1}/{len(paths)} fixtures", flush=True)
target.write_text(json.dumps(fixtures, ensure_ascii=False, indent=2) + "\n")
print(f"Imported {len(fixtures)} Native fixtures ({sum(f['accepted'] for f in fixtures)} accepted by upstream)")
