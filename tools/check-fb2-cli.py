#!/usr/bin/env python3
"""Compare installed Kotlin CLI with independent, pinned FB2 CLI expectations.

--import regenerates expectations with Pandoc 3.12.1. The default CI invocation
needs only the installed Kotlin launcher and pinned upstream input files.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
source = root / 'upstream/pandoc'
target = root / 'core/src/test/resources/fb2/cli.json'
parser = argparse.ArgumentParser()
parser.add_argument('--import', dest='regenerate', action='store_true')
args = parser.parse_args()
paths = sorted(subprocess.check_output(['git', '-C', str(source), 'ls-files', 'test/*.fb2', 'test/**/*.fb2'], text=True).splitlines())
if args.regenerate:
    assert subprocess.check_output(['pandoc', '--version'], text=True).splitlines()[0] == 'pandoc 3.12.1'
    fixtures = []
    for path in paths:
        result = subprocess.run(['pandoc', '-f', 'fb2', '-t', 'fb2', path], cwd=source, capture_output=True, timeout=30)
        assert result.returncode == 0, (path, result.stderr)
        fixtures.append({'path': path, 'sha256': hashlib.sha256((source/path).read_bytes()).hexdigest(),
                         'stdout': result.stdout.decode(), 'stderr': result.stderr.decode()})
    target.write_text(json.dumps(fixtures, ensure_ascii=False, indent=2) + '\n')
    print(f'Imported {len(fixtures)} original FB2 CLI conversions')
else:
    fixtures = json.loads(target.read_text())
    assert [f['path'] for f in fixtures] == paths
    launcher = root / 'cli/build/install/cli/bin/cli'
    for fixture in fixtures:
        assert hashlib.sha256((source/fixture['path']).read_bytes()).hexdigest() == fixture['sha256']
        result = subprocess.run([str(launcher), '-f', 'fb2', '-t', 'fb2', fixture['path']], cwd=source,
                                capture_output=True, timeout=30)
        assert result.returncode == 0, (fixture['path'], result.stderr)
        assert result.stdout.decode() == fixture['stdout'], f"FB2 stdout mismatch: {fixture['path']}"
        assert result.stderr.decode() == fixture['stderr'], f"FB2 stderr mismatch: {fixture['path']}"
    print(f'Verified installed CLI against {len(fixtures)} original FB2 conversions, including embedded images')
