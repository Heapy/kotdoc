#!/usr/bin/env python3
"""Source-only Pandoc Command/Old/Lua inventory. Never executes a command test.

Old helper formulas translated from Tests/Old.hs, Copyright (C) 2006-2024
John MacFarlane; GPL-2.0-or-later. Original source and notice remain upstream.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
COMMAND_CAPTURE = ROOT / 'docs/test-inventory-command-blocks.json'
OLD = 'upstream/pandoc/test/Tests/Old.hs'
LUA = 'upstream/pandoc/pandoc-lua-engine/test/'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def source(path):
    return dict(path=path, sha256=sha((ROOT / path).read_bytes()))


def capture_commands(write=True):
    version = subprocess.check_output(['pandoc', '--version'], text=True).splitlines()[0]
    if version != 'pandoc 3.12.1':
        raise ValueError('Inventory AST capture requires pinned pandoc 3.12.1')
    def capture(path):
        # Exactly readMarkdown def{readerExtensions=pandocExtensions} as original.
        data = json.loads(subprocess.check_output(['pandoc', '--preserve-tabs', '-f', 'markdown', '-t', 'json', str(path)]))
        return dict(**source(str(path.relative_to(ROOT))), blocks=[b['c'][1] for b in data['blocks'] if b['t'] == 'CodeBlock'])
    paths = sorted((ROOT / 'upstream/pandoc/test/command').glob('*.md'))
    with ThreadPoolExecutor(max_workers=8) as pool:
        files = list(pool.map(capture, paths))
    out = dict(schema_version=1, oracle_version=version,
               purpose='Source AST discovery only; NO contained test commands are executed.',
               command=['pandoc', '--preserve-tabs', '-f', 'markdown', '-t', 'json', '<original-source.md>'],
               semantics=source('upstream/pandoc/test/Tests/Command.hs'), files=files)
    if write:
        COMMAND_CAPTURE.write_text(json.dumps(out, ensure_ascii=False, indent=2) + '\n')
    return out


def fenced_blocks(text):
    """Bounded pinned-corpus grammar, cross-checked against original Markdown AST.

    Only root fences with <=3 leading spaces and outside HTML comments are
    accepted. Future nested/indented/raw-HTML cases fail the oracle comparison;
    this is deliberately not advertised as a general Markdown parser.
    """
    if '\r' in text:
        raise ValueError('Raw CR needs direct-library capture; CLI Input strips CR')
    lines = text.split('\n')
    out, i, comment = [], 0, False
    while i < len(lines):
        line = lines[i]
        if comment:
            comment = '-->' not in line
            i += 1
            continue
        if '<!--' in line:
            comment = '-->' not in line.split('<!--', 1)[1]
            i += 1
            continue
        match = re.fullmatch(r'( {0,3})(`{3,}|~{3,})(.*)', line)
        if not match:
            i += 1
            continue
        indent, marker = len(match[1]), match[2]
        i += 1
        body = []
        close = r' {0,3}' + re.escape(marker[0]) + '{' + str(len(marker)) + r',}[ \t]*'
        while i < len(lines) and not re.fullmatch(close, lines[i]):
            value = lines[i]
            remove = min(indent, len(value) - len(value.lstrip(' ')))
            body.append(value[remove:])
            i += 1
        if i == len(lines):
            raise ValueError('Unclosed source fence needs grammar audit')
        out.append('\n'.join(body))
        i += 1
    return out


def haskell_lines(text):
    """Haskell lines separates LF only, not Unicode separators or CR."""
    return text.split('\n')[:-1] if text.endswith('\n') else text.split('\n') if text else []


def command_parts(code):
    lines = haskell_lines(code)
    continuations = []
    while lines and lines[0].endswith('\\'):
        continuations.append(lines.pop(0)[:-1])
    # take 1 r1 may be empty: continuation-only blocks still form a command.
    joined = ' '.join(continuations + lines[:1])
    lines = lines[1:]
    if not joined.startswith('%'):
        raise ValueError('Command test line does not begin with %')
    command = joined[1:].lstrip(' ')  # Original dropWhile (== ' '), not Unicode strip.
    # break/drop semantics intentionally accept a missing ^D as upstream does.
    split = lines.index('^D') if '^D' in lines else len(lines)
    stdin = ''.join(x + '\n' for x in lines[:split])
    norm = lines[split + 1:]
    if '.' in norm:
        norm = norm[:norm.index('.')]
    combined = ''.join(x + '\n' for x in norm)
    stdout, stderr = [], []
    status = 0
    status_explicit = bool(norm and re.fullmatch(r'=> -?\d+', norm[-1]))
    payload = norm[:-1] if status_explicit else norm
    if status_explicit:
        status = int(norm[-1][3:])
    # execTest prefixes stderr lines first. Retain combined golden as authority;
    # stream decomposition is ambiguous when stdout itself starts with "2> ".
    prefix = True
    for line in payload:
        if prefix and line.startswith('2> '):
            stderr.append(line[3:])
        else:
            prefix = False
            stdout.append(line)
    return dict(command=command, stdin=stdin, expected_combined=combined,
                expected_stdout=''.join(x + '\n' for x in stdout),
                expected_stderr=''.join(x + '\n' for x in stderr), expected_exit_status=status,
                exit_marker_explicit=status_explicit,
                decomposition='Conventional leading 2> stderr and final => integer; combined golden is authoritative, channel prefixes may collide with literal stdout.')


def command_inventory():
    capture = json.loads(COMMAND_CAPTURE.read_text())
    if capture['oracle_version'] != 'pandoc 3.12.1' or capture['command'] != ['pandoc', '--preserve-tabs', '-f', 'markdown', '-t', 'json', '<original-source.md>']:
        raise ValueError('Unexpected Command AST oracle version/options')
    paths = sorted(str(p.relative_to(ROOT)) for p in (ROOT / 'upstream/pandoc/test/command').glob('*.md'))
    if paths != [f['path'] for f in capture['files']]:
        raise ValueError('Command file inventory changed; refresh pinned AST capture')
    if capture['semantics'] != source('upstream/pandoc/test/Tests/Command.hs'):
        raise ValueError('Command registry/parser source drift')
    records = []
    for f in capture['files']:
        if source(f['path']) != {k: f[k] for k in ('path', 'sha256')}:
            raise ValueError('Command source drift: ' + f['path'])
        if fenced_blocks((ROOT / f['path']).read_bytes().decode('utf-8')) != f['blocks']:
            raise ValueError('Source fence extraction differs from independent original AST: ' + f['path'])
        if not f['blocks']:
            records.append(dict(id='pandoc/command/' + Path(f['path']).name + '/!!', kind='empty-command-file-failure', source={k:f[k] for k in ('path','sha256')}, status='unmapped', kotlin=None, gap='Original registration creates assertFailure when no CodeBlocks exist.'))
        for n, code in enumerate(f['blocks'], 1):
            parts = command_parts(code)
            records.append(dict(id='pandoc/command/' + Path(f['path']).name + '/#' + str(n),
                                kind='source-command-golden', source={k:f[k] for k in ('path','sha256')},
                                code_sha256=sha(code.encode()), source_code=code, **parts,
                                expectation_origin=f['path'] + ': top-level CodeBlock #' + str(n),
                                upstream_execution=dict(cwd='upstream/pandoc/test', shell=parts['command'], semantics='Tests.Command.execTest: pandocToEmulate rewrites pandoc invocations; CR removed; stderr prefix and status appended'),
                                status='unmapped', kotlin=None,
                                gap='No reviewed executable Kotlin CLI stdout/stderr/status assertion mapping. Reader-only or component observations do not discharge this command.'))
    return records


# A deliberately small Haskell expression parser, not regex call-site counting.
# It consumes the entire pinned tests expression, including map lambdas, ++ and $.
TOKEN = re.compile(r'\s+|--[^\n]*|"(?:[^"\\]|\\.)*"|[A-Za-z_][A-Za-z0-9_\']*|\+\+|->|[\[\](),$\\]')


class RegistrationParser:
    def __init__(self, text):
        self.tokens = []
        position = 0
        for m in TOKEN.finditer(text):
            if m.start() != position:
                raise ValueError('Unsupported Old registration syntax near ' + text[position:position+60])
            position = m.end()
            if not m[0].isspace() and not m[0].startswith('--'):
                self.tokens.append(m[0])
        if position != len(text):
            raise ValueError('Unconsumed Old registration suffix')
        self.i = 0

    def take(self, token=None):
        v = self.tokens[self.i]
        self.i += 1
        if token is not None and v != token:
            raise ValueError(f'Expected {token}, got {v}')
        return v

    def expression(self):
        x = self.concat()
        if self.i < len(self.tokens) and self.tokens[self.i] == '$':
            self.take('$'); x = ('call', x, self.expression())
        return x

    def concat(self):
        x = self.application()
        while self.i < len(self.tokens) and self.tokens[self.i] == '++':
            self.take('++'); x = ('plus', x, self.application())
        return x

    def application(self):
        x = self.atom()
        while self.i < len(self.tokens) and self.tokens[self.i] not in (')', ']', ',', '$', '++'):
            x = ('call', x, self.atom())
        return x

    def atom(self):
        t = self.take()
        if t == '[':
            xs = []
            while self.tokens[self.i] != ']':
                xs.append(self.expression())
                if self.tokens[self.i] != ',':
                    break
                self.take(',')
            self.take(']'); return ('list', xs)
        if t == '(':
            x = self.expression(); self.take(')'); return x
        if t == '\\':
            name = self.take(); self.take('->'); return ('lambda', name, self.expression())
        if t.startswith('"'):
            return ('string', json.loads(t))
        if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_']*", t):
            return ('var', t)
        raise ValueError('Unexpected token: ' + t)

    def parse(self):
        x = self.expression()
        if self.i != len(self.tokens):
            raise ValueError('Unconsumed registration tokens')
        return x


def curry(fn, n, args=()):
    return lambda x: fn(*(args + (x,))) if len(args) + 1 == n else curry(fn, n, args + (x,))


def evaluate(node, env):
    tag, *args = node
    if tag == 'string': return args[0]
    if tag == 'var': return env[args[0]]
    if tag == 'list': return [evaluate(x, env) for x in args[0]]
    if tag == 'call': return evaluate(args[0], env)(evaluate(args[1], env))
    if tag == 'plus': return evaluate(args[0], env) + evaluate(args[1], env)
    if tag == 'lambda': return lambda x: evaluate(args[1], dict(env, **{args[0]: x}))
    raise ValueError(tag)


def old_inventory():
    if source(OLD)['sha256'] != '4f4b9708d447dc1400a05a29f24e5cf2582b488524b12682ab7d6aa1c16af08d':
        raise ValueError('Old helper/source contract changed; audit extraction formulas before refreshing')
    text = (ROOT / OLD).read_text()
    expr = text.split('tests pandocPath =\n', 1)[1].split('\n where\n', 1)[0]
    def leaf(name, opts, inp, norm, normalizer='identity'):
        return dict(name=name, groups=[], options=opts, input=inp, expected=norm, normalizer=normalizer)
    def group(name, xs):
        flat = []
        def visit(values):
            for x in values:
                if isinstance(x, list): visit(x)
                else: flat.append(dict(x, groups=[name] + x['groups']))
        visit(xs)
        return flat
    def writers(fmt):
        opts = ['-r', 'native', '-w', fmt, '--columns=80', '--variable', 'pandoc-version=']
        return [leaf('basic', opts + ['-s'], 'testsuite.native', 'writer.' + fmt),
                leaf('tables', opts, 'tables.native', 'tables.' + fmt)]
    def extended(fmt):
        opts = ['-r', 'native', '-w', fmt, '--columns=80', '--variable', 'pandoc-version=']
        return writers(fmt) + [leaf(n + ' table', opts, 'tables/' + n + '.native', 'tables/' + n + '.' + fmt) for n in ('planets', 'nordics', 'students')]
    def lhs_writer(fmt):
        return [leaf(n, ['--wrap=preserve', '-r', 'native', '--markdown-headings=setext', '-w', f], 'lhs-test.native', 'lhs-test.' + f) for n, f in [('lhs to normal', fmt), ('lhs to lhs', fmt + '+lhs')]]
    def lhs_reader(fmt):
        return leaf('lhs', ['-r', fmt, '-w', 'native'], 'lhs-test.' + fmt, 'lhs-test-markdown.native' if fmt == 'markdown+lhs' else 'lhs-test.native')
    def s5(name, opts, fmt):
        return leaf(fmt + ' writer (' + name + ')', ['-r', 'native', '-w', fmt] + opts + ['--variable', 'pandoc-version='], 's5.native', 's5-' + name + '.html')
    env = {"test'": curry(leaf, 4), 'testGroup': curry(group, 2), 'mconcat': lambda xs: sum(xs, []),
           'map': curry(lambda f, xs: [f(x) for x in xs], 2), "writerTests'": writers,
           "extWriterTests'": extended, "lhsWriterTests'": lhs_writer, "lhsReaderTest'": lhs_reader,
           "s5WriterTest'": curry(s5, 3),
           "fb2WriterTest'": curry(lambda title, opts, inp, norm: leaf(title, ['-t','fb2'] + opts, inp, norm, 'Tests.Old.ignoreBinary . formatXML'), 4)}
    tree = evaluate(RegistrationParser(expr).parse(), env)
    def flatten(xs):
        for x in xs:
            if isinstance(x, list): yield from flatten(x)
            else: yield x
    out = []
    for x in flatten(tree):
        inp = source('upstream/pandoc/test/' + x['input'])
        expected = source('upstream/pandoc/test/' + x['expected'])
        out.append(dict(id='pandoc/old/' + '/'.join(x['groups'] + [x['name']]) + '/' + x['input'] + '->' + x['expected'],
                        kind='source-legacy-golden', source=source(OLD), name=x['name'], groups=x['groups'],
                        input=inp, expectation_origin=expected, normalizer=x['normalizer'],
                        expected_processing=["UTF8.readFile via strict readFile'", x['normalizer']],
                        actual_processing=['UTF8.toStringLazy stdout', x['normalizer'], "filter (/='\\r')"],
                        expected_exit_status=0, stderr_contract='Not compared by this golden assertion',
                        upstream_execution=dict(cwd='upstream/pandoc/test', argv=['test-pandoc','--emulate','--quiet',x['input']] + x['options']),
                        status='unmapped', kotlin=None,
                        gap='No independently reviewed complete original golden/application assertion mapping; asset reuse is not a port.'))
    # Source registers mediawiki basic/tables twice via writerTests + extendedWriterTests.
    # Preserve both registrations, rather than silently deduplicating tests.
    counts = {}
    for record in out:
        identity = record['id']
        counts[identity] = counts.get(identity, 0) + 1
        record['registration_occurrence'] = counts[identity]
        record['id'] += '/occurrence-' + str(counts[identity])
    return out


def lua_inventory():
    out = []
    main = LUA + 'test-pandoc-lua-engine.hs'
    registration = (ROOT / main).read_text().split('tests = testGroup', 1)[1]
    modules = re.findall(r'testGroup "([^"]+)" (Tests\.Lua(?:\.\w+)?).tests', registration)
    if len(modules) != 4: raise ValueError('Lua main registration shape changed')
    for group_name, module in modules:
        path = LUA + module.replace('.', '/') + '.hs'
        text = (ROOT / path).read_text()
        body = text.split('tests =', 1)[1].split('\n where\n', 1)[0]
        if module == 'Tests.Lua': body = body.split('\nassertFilterConversion ::', 1)[0]
        patterns = [(r'\b(testCase|goldenVsString) "((?:[^"\\]|\\.)*)"', 'haskell-lua-assertion'),
                    (r'\b(testPandocLua) "([^"]+)"\s*\("lua" </> "module" </> "([^"]+)"\)', 'lua-script-suite')]
        matches = []
        for pattern, kind in patterns:
            for m in re.finditer(pattern, body): matches.append((m.start(), m, kind))
        for _, m, kind in sorted(matches):
            name = json.loads('"' + m[2] + '"')
            origin = source(path)
            if kind == 'lua-script-suite': origin = source(LUA + 'lua/module/' + m[3])
            # All Haskell names in these four pinned registries are unique.
            groups = [group_name]
            if module == 'Tests.Lua':
                nested_start = body.index('testGroup "global modules"')
                nested_end = body.index('\n    ]', nested_start)
                if nested_start < m.start() < nested_end: groups.append('global modules')
            out.append(dict(id='pandoc/lua/' + '/'.join(groups) + '/' + name, name=name, groups=groups, kind=kind,
                            source=source(path), registration_function=m[1], expectation_origin=origin,
                            upstream_execution=dict(command='cabal test pandoc-lua-engine:test-pandoc-lua-engine', cwd='upstream/pandoc'),
                            status='unmapped', kotlin=None,
                            gap='Lua runtime assertion not mapped. Script suite records require separate nested Lua test-tree enumeration.' if kind == 'lua-script-suite' else 'Original Lua engine assertion not mapped; analogous Kotlin API tests do not establish engine coverage.'))
    ids = [x['id'] for x in out]
    if len(ids) != len(set(ids)): raise ValueError('Duplicate Lua identity')
    return dict(main_registration=source(main), groups=[dict(name=g, module=m) for g,m in modules], registrations=out,
                gap='All four Haskell module registrations enumerated; nested tasty-Lua script leaves and feature/runtime behavior remain pending.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument('--capture-command-ast', action='store_true')
    actions.add_argument('--verify-command-ast', action='store_true')
    args = parser.parse_args()
    if args.verify_command_ast:
        actual = capture_commands(write=False)
        if actual != json.loads(COMMAND_CAPTURE.read_text()):
            raise ValueError('Independent original AST replay differs from capture')
        print('Verified original Markdown AST replay for', len(actual['files']), 'files; no contained commands executed')
    elif args.capture_command_ast:
        x = capture_commands()
        print('Captured', len(x['files']), 'original Markdown AST files without executing contained commands')
    else:
        print(json.dumps(dict(command=command_inventory(), old=old_inventory(), lua=lua_inventory()), ensure_ascii=False, indent=2))
