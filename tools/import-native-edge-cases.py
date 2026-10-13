#!/usr/bin/env python3
"""Pinned Pandoc oracle for Native lexical, numerical, and column-layout boundaries."""
import json
from pathlib import Path
import random
import struct
import subprocess

root = Path(__file__).resolve().parents[1]
assert subprocess.check_output(["pandoc", "--version"], text=True).splitlines()[0] == "pandoc 3.12.1"
syntax = [
    'Str "x"', '(Str "x")', '((Str "x"))', '(Str) "x"', '[]', '[ ]',
    'Header -3 ("",[],[]) []', 'Header (-3) ("",[],[]) []', 'Header 0x10 ("",[],[]) []',
    'Header 0o20 ("",[],[]) []', 'Header 9223372036854775808 ("",[],[]) []',
    'Header (-9223372036854775809) ("",[],[]) []', 'Header 1.0 ("",[],[]) []',
    '[Para []] -- comment', '[Para []] {- c -}', 'Pandoc (Meta (fromList [])) []',
    'Pandoc Meta{unMeta=fromList[("a",MetaString "x"),("a",MetaString "y")]} []',
    'Str "\\xD800"', 'Str "\\128512\\&1"', 'Str "raw\nnewline"', 'Str "\\^?"',
    'Str "\\^@\\^_"', 'Str "\\NUL\\SOH"', 'Str "a" ++ []', '[Str "x",HorizontalRule]',
    'Str "\\1114112"', 'Str "a\\\n \\b"', 'Str "\\o12\\x20"',
]
# GHC 9.12 Read also accepts binary literals in Integer and Double positions.
for number in ['0b101','0B101','-0b101','(-0B101)','0b'+'1'*70,'0b','0b102']:
    syntax.append(f'Header {number} ("",[],[]) []')
    syntax.append(f'Table ("",[],[]) (Caption Nothing []) [(AlignDefault,ColWidth {number})] (TableHead ("",[],[]) []) [] (TableFoot ("",[],[]) [])')
read_cases = []
for text in syntax:
    p = subprocess.run(['pandoc', '-f', 'native', '-t', 'json'], input=text, text=True, capture_output=True)
    read_cases.append({'input': text, 'accepted': p.returncode == 0, 'json': json.loads(p.stdout) if p.returncode == 0 else None})

r = random.Random(0x444F55424C45)
numbers = ['0.0', '-0.0', 'NaN', '-NaN', 'Infinity', '-Infinity', '1e999', '1e-999', '0x10', '0o10',
           '1e23', '4.9e-324', '2.2250738585072014e-308', '1e-2', '0.1', '1e6', '1e7']
while len(numbers) < 517:
    number = struct.unpack('>d', r.getrandbits(64).to_bytes(8, 'big'))[0]
    if number == number and abs(number) != float('inf'):
        numbers.append(repr(number))
table = ('Table ("",[],[]) (Caption Nothing []) [' + ','.join(f'(AlignDefault,ColWidth ({n}))' for n in numbers) +
         '] (TableHead ("",[],[]) []) [] (TableFoot ("",[],[]) [])')
unicode = ''.join(chr(i) for i in range(128)) + 'Привет 日本語 😀1\uE000\U00010000'
unicode_json = {'pandoc-api-version':[1,23,1,2], 'meta':{}, 'blocks':[{'t':'Para','c':[{'t':'Str','c':unicode}]}]}
unicode_native = subprocess.check_output(['pandoc','-f','json','-t','native'], input=json.dumps(unicode_json).encode()).decode()
writer_cases = []
for name, text in [('numbers', table), ('unicode', unicode_native), ('nested', 'Pandoc Meta { unMeta = fromList [("title", MetaString "Test")] } [BlockQuote [Para [Str "a",Space,Emph [Str "b"],LineBreak,Str "c"]]]')]:
    for columns in [1, 2, 10, 20, 40, 72, 80, 10000]:
        for standalone in [False, True]:
            command = ['pandoc','-f','native','-t','native',f'--columns={columns}'] + (['--standalone'] if standalone else [])
            expected = subprocess.check_output(command, input=text.encode()).decode()
            writer_cases.append({'name':name, 'input':text, 'columns':columns, 'standalone':standalone,
                                 'expected':expected if standalone else expected.removesuffix('\n')})
output = root/'core/src/test/resources/native/edge-cases.json'
output.write_text(json.dumps({'reader':read_cases, 'writer':writer_cases}, ensure_ascii=False, indent=2)+'\n')
print(f'Imported {len(read_cases)} syntax cases and {len(writer_cases)} layout cases (517 numeric values)')
