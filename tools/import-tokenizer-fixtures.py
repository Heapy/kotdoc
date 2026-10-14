#!/usr/bin/env python3
"""Independent original Skylighting token/trace/error observations and tests."""
import argparse, atexit, collections, gzip, hashlib, itertools, json, os, random, re, subprocess
from pathlib import Path
root = Path(__file__).resolve().parents[1]
target = root / 'core/src/test/resources/highlighting/tokenizer.jsonl.gz'
p = argparse.ArgumentParser()
p.add_argument('--check-source', action='store_true')
p.add_argument('--oracle', default=os.environ.get('KOTDOC_TOKENIZER_REFERENCE'))
args = p.parse_args()
sources = ['tools/import-tokenizer-fixtures.py', 'licenses/Skylighting-core-BSD-3-Clause.txt',
           'licenses/Utf8-string-BSD-3-Clause.txt', 'core/src/test/resources/highlighting/syntax-binary.jsonl.gz']
for folder in ['vendor/skylighting-core-0.15', 'vendor/utf8-string-1.0.2', 'tools/haskell-tokenizer-reference']:
    sources += sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
def hashes(): return {n: hashlib.sha256((root/n).read_bytes()).hexdigest() for n in sources}
def digest(cases): return hashlib.sha256(''.join(json.dumps(c, sort_keys=True, separators=(',', ':')) for c in cases).encode()).hexdigest()
def text_lines(text):
    return text.split('\n')[:-1] if text.endswith('\n') else text.split('\n') if text else []
if args.check_source:
    with gzip.open(target, 'rt') as f:
        manifest = json.loads(next(f)); cases = [json.loads(line) for line in f]
    assert manifest['sources'] == hashes()
    assert manifest['cases'] == len(cases) and manifest['caseHash'] == digest(cases)
    assert manifest['groups'] == dict(collections.Counter(c['group'] for c in cases))
    assert manifest['groups']['golden'] == 32 and manifest['groups']['regression'] == 21
    assert manifest['groups']['mixed'] == 195 * 32 and manifest['groups']['fuzz'] == 195 * 100
    assert len(set(c['input'][0] for c in cases if c['group'] == 'mixed')) == 195
    for c in cases:
        if 'original' in c: assert c['original'] == c['expected']['result']
        if c.get('noDrop'):
            assert c['expected']['result']['tokens'] is not None
            assert [''.join(t[1] for t in line) for line in c['expected']['result']['tokens']] == text_lines(c['input'][-2])
    print(f'Verified {len(cases)} tokenizer observations, {len(manifest["sources"])} source files')
    raise SystemExit
assert args.oracle, '--oracle required'
process = subprocess.Popen([args.oracle], cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
def cleanup():
    if process.poll() is None:
        process.terminate(); process.wait()
atexit.register(cleanup)
cases = []
def native(op, value):
    process.stdin.write(json.dumps([op, value], ensure_ascii=True) + '\n'); process.stdin.flush()
    line = process.stdout.readline(); assert line, (op, value)
    return json.loads(line)
def add(op, value, group='synthetic', **extra):
    expected = native(op, value)
    case = dict(op=op, input=value, group=group, expected=expected, **extra)
    if 'original' in extra: assert extra['original'] == expected['result'], (group, extra, expected)
    if extra.get('noDrop'):
        assert [''.join(t[1] for t in line) for line in expected['result']['tokens']] == text_lines(value[-2]), (value, expected)
    cases.append(case)
    if len(cases) % 500 == 0: print(f'Captured {len(cases)} tokenizer cases', flush=True)
def literal(s): return native('literal', s)['result']
# These exact files remain unchanged in vendor. Native Read only decodes their
# independent expected tokens; Kotlin cannot contribute any expectation.
case_files = sorted((root/'vendor/skylighting-core-0.15/test/cases').iterdir())
assert len(case_files) == 32
for path in case_files:
    text = path.read_bytes().decode('utf8')
    expected_path = root/'vendor/skylighting-core-0.15/test/expected'/(path.name+'.native')
    original = native('golden', expected_path.read_bytes().decode('utf8'))['result']
    add('builtin', [path.suffix[1:], text, False], 'golden', file=str(path.relative_to(root)), original=original)
# Retain every explicit original tokenizer regression expectation, not merely a
# generated reference result. Haskell Read interprets all original escape forms.
source = (root/'vendor/skylighting-core-0.15/test/test-skylighting.hs').read_text()
reg = source[source.index('[ testCase "perl NUL case"'):source.index('-- | A syntax definition')]
starts = list(re.finditer(r'testCase\s+("(?:[^"\\]|\\.)*")', reg))
aliases = dict(perl='Perl', html='HTML', cpp='CPP', bash='Bash', c='C', dosbat='MS-DOS Batch', cmake='CMake', lua='Lua', awk='AWK', glsl='GLSL', makefile='Makefile', markdown='Markdown')
loop_start = source.index('loopSyntax =')
loop_end = source.index('compareValues ::', loop_start)
loop_xml = ''.join(literal(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"', source[loop_start:loop_end])[1:])
for index, match in enumerate(starts):
    block = reg[match.end():starts[index+1].start() if index+1<len(starts) else len(reg)]
    expected_text = block[block.index('Right')+5:block.index('@=?')].strip()
    original = native('golden', expected_text)['result']
    name = literal(match.group(1))
    if 'loopSyntax' in block:
        add('xml', ['loop.xml', loop_xml, 'xy', False], 'regression', name=name, original=original)
    else:
        m = re.search(r'tokenize\s+defConfig\s+(\w+)\s+("(?:[^"\\]|\\.)*")', block)
        assert m, block
        add('builtin', [aliases[m[1]], literal(m[2]), False], 'regression', name=name, original=original)
assert len(starts) == 21
with gzip.open(root/'core/src/test/resources/highlighting/syntax-binary.jsonl.gz', 'rt') as f:
    next(f); models = {c['input']:c['expected']['value'] for c in map(json.loads, f) if c['op']=='baked'}
assert len(models) == 195
for name in sorted(models):
    for path in case_files:
        add('builtin', [name, path.read_bytes().decode('utf8')[:240], False], 'mixed', noDrop=True)
rng = random.Random(0x5a17)
alphabet = 'aA09_ \\t\n\r\x00<>[](){}%\\\'"$#@;:=+-*/' + 'éßΣ中😀\ufffe\uffff\u2000\u00a0\u0301\U00031350'
fuzz = ['', '\n', '\n\n', '\r\n', '\t\t', '\ufffe\uffff', 'aéΣ😀9', '中\x00a'] + [''.join(rng.choice(alphabet) for _ in range(rng.randrange(1,120))) for _ in range(12)]
for _ in range(80):
    points = [rng.choice(alphabet) if rng.randrange(3) else chr((lambda p: p if p < 0xd800 else p+0x800)(rng.randrange(0x110000-0x800))) for _ in range(rng.randrange(1,120))]
    fuzz.append(''.join(points))
assert len(fuzz) == 100
for name in sorted(models):
    for text in fuzz: add('builtin', [name, text, False], 'fuzz', noDrop=True)
D = sorted(set(ord(c) for c in ' \t\n\r.,;:!?()[]{}+-*/=<>%&|^~\\\'"'))
def rule(m, attr='KeywordTok', **kw):
    result = [m,attr,False,D,False,True,[],False,False,None,[]]
    for name,index in dict(include=2,delims=3,dynamic=4,sensitive=5,children=6,lookahead=7,first=8,column=9,switches=10).items():
        if name in kw: result[index]=kw[name]
    return result
def ctx(name, rules=(), attr='NormalTok', **kw):
    return [name,'A',list(rules),attr,kw.get('empty',[]),kw.get('end',[]),kw.get('fallthrough',False),kw.get('fall',[]),kw.get('dynamic',False)]
def syn(contexts, starting='start', name='A'):
    return [name,'a.xml','A',[],[[c[0],c] for c in contexts],'','','',[],starting]
def model(s, text, trace=True, mp=None, **extra):
    data = [s,mp if mp is not None else [[s[0],s]],text,trace]
    op = 'model'
    if any(0xd800 <= ord(c) <= 0xdfff for c in text):
        data[2] = list(map(ord,text)); op = 'model-points'
    add(op,data,**extra)
def single(m, text, **kw): model(syn([ctx('start',[rule(m, **kw)])]), text)
def push(name,syntax='A'): return [[syntax,name]]
# Every matcher has positive/negative, Unicode and physical-line cases.
matchers = [['DetectChar',ord('x')],['Detect2Chars',ord('x'),ord('y')],['AnyChar',[ord('x'),0xfffd,0x1f600]],['RangeDetect',ord('<'),ord('>')],['StringDetect','xy'],['WordDetect','xy'],['Keyword',[True,D],['Right',[True,['xy','foo','Straße','😀']]]],['Int'],['Float'],['HlCOct'],['HlCHex'],['HlCStringChar'],['HlCChar'],['LineContinue',ord('\\')],['DetectSpaces'],['DetectIdentifier']]
texts = ['', '\n', 'xy', 'xyz xy!', 'a xy', '<a😀é>tail', '<unterminated', 'foo', 'Straße', '😀', '\ufffe\uffff', '  \txy', '\u00a0x', '123', '-123', '0123', '0x1Af', '0x', '.5', '5.', '5e2', '1.5e+', '1.5e-2x', '.', "'a'", "'é'", "'\\0'", "'\\x1a'", '\\123', '\\0', '\\0777', '\\x123', '\\x', '\\n', '\\', '\\\r', '\\\nxy', 'x\r\n\nxy']
for m,text in itertools.product(matchers,texts): single(m,text)
for cp in [0,9,32,65,160,233,0x3a3,0x4e2d,0xd800,0xdfff,0xfffd,0xfffe,0xffff,0x1f600]:
    for kind in ['DetectChar','Detect2Chars','RangeDetect','LineContinue']:
        m=[kind,cp]+([cp] if kind in ['Detect2Chars','RangeDetect'] else [])
        for text in [chr(cp),chr(cp)*2,'x'+chr(cp),chr(cp)+'\n'+chr(cp)]: single(m,text)
for text in ['xy','XY','xY','xy!','axy','xy_','!xy','Straße','STRASSE','SS','ß','Σ','σ','ς','😀','\ufffe']:
    for pattern in ['xy','Straße','SS','Σ','😀','']:
        for kind in ['StringDetect','WordDetect']:
            for sensitive in [True,False]: single([kind,pattern],text,sensitive=sensitive)
for col,first,text in itertools.product([None,-1,0,1,2,3,8,2**63-1],[False,True],['xy',' xy','\txy','😀xy','中 xy','\n xy','\u00a0xy']):
    single(['StringDetect','xy'],text,column=col,first=first)
for delims,text in itertools.product([[],[10],[32],[10,32,ord('@')],D],['5','@5','x@5',' 5','.5','x .5','foo','foo!','!foo']):
    for m in [['Int'],['Float'],['WordDetect','foo'],['Keyword',[True,delims],['Right',[True,['foo']]]]]: single(m,text,delims=delims)
# Compilation failure priority, zero matches, outer word-boundary check and
# group captures from assertions/subroutines all come from original code.
patterns = ['', '^', '$', '.', 'x+', 'x*', '\\bxy', '\\b{2}', '{2}', '(', '(x)(y)', '(?=(x))x', '(?<=(x))y', '(x)\\1', '(x)(?1)', '[\\pL]+', '(?i)ß', 'x+?', 'x++', '(%1)', '%1', '%1+', '%1+?', '%1++', '(?=%1)%1', '(?!%1)x']
for pattern,text in itertools.product(patterns,['xy','xxy','!xy','ß','😀','\ufffe']):
    single(['RegExpr',[pattern.encode().hex(),True,False]],text)
for pattern in [b'\xff',b'\xc0\x80',b'\xef\xbf\xbe',b'\x00']:
    single(['RegExpr',[pattern.hex(),True,False]],'xy')
# Parent captures transfer to switched context. Dynamic strings/chars/regexes
# read that context, including empty captures and literal metacharacters.
for capture_pattern,body in [('(xy)', 'xy'),('()', ''),('(.)', '*'),('(.)','😀'),('(a)(b)', 'ab'),('(x)()', 'x')]:
    parent=rule(['RegExpr',[capture_pattern.encode().hex(),True,False]],switches=[push('next')])
    for m in [['StringDetect','%1'],['StringDetect','%1x'],['StringDetect','%%1'],['StringDetect','%1%2'],['StringDetect','%10'],['DetectChar',49],['Detect2Chars',49,50]]+[['RegExpr',[p.encode().hex(),True,False]] for p in ['%1','%1+', '%1+?', '%1++','(%1)','(?=%1)%1','(?!%1)x','%1|x']]:
        s=syn([ctx('start',[parent]),ctx('next',[rule(m,attr='StringTok',dynamic=True)])])
        for text in [body+body+'x',body+'\n'+body+'x']: model(s,text)
# Children inherit consumed input, but do not inherit attributes. A successful
# child may clear captures; lookahead restores consumption, not context changes.
for child,lookahead in itertools.product([
    rule(['StringDetect','y'],attr='StringTok'),
    rule(['RegExpr',['287929',True,False]],attr='StringTok'),
    rule(['StringDetect','z']),
    rule(['StringDetect','y'],switches=[push('next')]),
], [False,True]):
    parent=rule(['RegExpr',['287829',True,False]],children=[child],lookahead=lookahead,switches=[push('other')])
    s=syn([ctx('start',[parent]),ctx('next',[],attr='CharTok'),ctx('other',[rule(['StringDetect','%1'],dynamic=True)],attr='OtherTok')])
    for text in ['xyx','xzx','xy\nxx']: model(s,text)
# IncludeRules matcher uses actual current context and only overrides NormalTok.
for attr,include,child in itertools.product(['NormalTok','KeywordTok'],[False,True],[False,True]):
    inc=rule(['IncludeRules',['A','included']],attr='StringTok',include=include,children=[rule(['StringDetect','y'])] if child else [])
    s=syn([ctx('start',[inc]),ctx('included',[rule(['StringDetect','x'],attr=attr, switches=[push('next')])]),ctx('next',[],attr='OtherTok')])
    model(s,'xyz')
for context in ['', 'start', 'missing']:
    s=syn([ctx('start',[rule(['IncludeRules',['B',context]])])])
    b=syn([ctx('start',[rule(['StringDetect','x'])])],name='B')
    for mp in [[['A',s]],[['A',s],['B',b]]]: model(s,'x',mp=mp)
# Context stacks, successive physical-line switches, fallthrough and line
# continuation, and unchanged-base-pop termination.
for empty,end,fall,text in itertools.product([[],[[]],[push('next')]], [[],[[]],[push('next')]], [[],[[]],[push('next')]], ['x','x\n\ny','\ny','x\\\ny','x\\\r\ny']):
    start=ctx('start',[rule(['LineContinue',92])],empty=empty,end=end,fall=fall)
    next_=ctx('next',[rule(['LineContinue',92])],attr='StringTok',empty=[[]],end=[[]])
    model(syn([start,next_]),text,trace=False)
for mode in ['lookahead','fallthrough','fall-context','line-end','line-empty']:
    start=ctx('start')
    next_=ctx('next',fall=[[]])
    if mode=='lookahead': start[2]=[rule(['AnyChar',[120]],lookahead=True,switches=[push('next')])]
    if mode=='fallthrough': start[6]=True
    if mode=='fall-context': start[7]=[push('next')]
    if mode=='line-end': start[5]=[push('start')]
    if mode=='line-empty': start[4]=[push('start')]
    model(syn([start,next_]),'\n' if mode=='line-empty' else 'xy',trace=True)
# Errors are observable even when syntax is supplied outside the config map.
for m in [['Keyword',[True,D],['Left','missing']], ['IncludeRules',['Missing','ctx']]]:
    single(m,'x'); single(m,'x',column=1)
for name in ['start','missing','']:
    s=syn([ctx('start',[rule(['StringDetect','x'],switches=[push(name)])])])
    model(s,'x',mp=[])
model(syn([],starting='missing'),'x')
model(syn([ctx('start')],starting=''),'x')
# Basic line cases have traces and independently verify exact LF/CR handling.
for text in ['', '\n','\n\n','a\n','a\r\n','a\n\n','\r','😀éΣ\x00','\ufffe\uffff']:
    model(syn([ctx('start')]),text)
# Lookahead/IncludeRules can produce Nothing and still consume child input;
# this quirk is explicitly observed rather than silently repaired.
for lookahead in [False,True]:
    included=rule(['StringDetect','x'],lookahead=True,switches=[push('next')])
    outer=rule(['IncludeRules',['A','included']],lookahead=lookahead,children=[rule(['StringDetect','xy'])])
    model(syn([ctx('start',[outer]),ctx('included',[included]),ctx('next',[],attr='OtherTok')]),'xyz')
# Literal surrogate input crosses Data.Text packing; expected text comes from
# native Aeson/Text replacement, independently of JVM UTF-8 replacement defaults.
for text in ['\ud800','a\udfffz','\ud800\ud800','\udfff\ud800']:
    model(syn([ctx('start')]),text)
# Trace Show is lazy: malformed IncludeRules hidden behind a failed column
# guard can be harmless with trace disabled, but throws when trace forces Show.
for trace,column,text in itertools.product([False,True],['0','1'],['','x']):
    xml='<language name="Bad"><highlighting><contexts><context name="start"><IncludeRules context="#stay" column="'+column+'"/></context></contexts></highlighting></language>'
    add('xml',['bad.xml',xml,text,trace])
# Include a compiler error behind failed column/first-nonspace guards.
for first,col,text in itertools.product([False,True],[None,0,1],['x',' x','😀x']):
    single(['RegExpr',['28',True,False]],text,first=first,column=col)
for trace,precedence in itertools.product([False,True],[0,10,11]):
    for mp in [[],[['A',syn([ctx('start')])]]]: add('config-show',[mp,trace,precedence])
process.stdin.close(); assert process.wait() == 0
manifest = dict(upstream='skylighting-core-0.15', sources=hashes(), cases=len(cases), caseHash=digest(cases),
                groups=dict(collections.Counter(c['group'] for c in cases)), seed='0x5a17', syntaxes=195)
raw = '\n'.join(json.dumps(x,ensure_ascii=True,separators=(',',':')) for x in [manifest]+cases)+'\n'
with target.open('wb') as out:
    with gzip.GzipFile(filename='',fileobj=out,mode='wb',mtime=0) as f: f.write(raw.encode())
print(json.dumps({k:v for k,v in manifest.items() if k!='sources'},indent=2),flush=True)
