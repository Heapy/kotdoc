#!/usr/bin/env python3
"""Finite FB2 nine-unit-leaf source/assertion bridge. No conversion or test execution."""
import argparse
import base64
import io
import zipfile
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
READER='upstream/pandoc/test/Tests/Readers/FB2.hs'
WRITER='upstream/pandoc/test/Tests/Writers/FB2.hs'
HELPERS='upstream/pandoc/test/Tests/Helpers.hs'
TEST='core/src/test/kotlin/io/heapy/kotdoc/formats/'
MAIN='core/src/main/kotlin/io/heapy/kotdoc/formats/'
spec=importlib.util.spec_from_file_location('fb2_runtime_inventory',ROOT/'tools/runtime-test-inventory.py')
runtime=importlib.util.module_from_spec(spec);spec.loader.exec_module(runtime)


def source(path):return dict(path=path,sha256=hashlib.sha256((ROOT/path).read_bytes()).hexdigest())


def hs_string(raw):
    if len(raw)<2 or raw[0]!='"' or raw[-1]!='"':raise ValueError('Expected original Haskell string literal')
    value='';i=1
    simple={'n':'\n','r':'\r','t':'\t','"':'"','\\':'\\'}
    while i<len(raw)-1:
        c=raw[i];i+=1
        if c!='\\':value+=c;continue
        if i>=len(raw)-1:raise ValueError('Unclosed Haskell escape')
        c=raw[i];i+=1
        if c in simple:value+=simple[c]
        elif c=='x':
            m=re.match(r'[0-9A-Fa-f]+',raw[i:])
            if not m:raise ValueError('Missing hexadecimal Haskell escape')
            value+=chr(int(m[0],16));i+=len(m[0])
        else:raise ValueError('Unsupported Haskell escape '+c)
    return value


def original_writer_cases(text):
    tokens=runtime_inventory_lexer(text)
    start=next(i for i,t in enumerate(tokens[:-2]) if t['value']=='tests' and tokens[i+1]['value']=='=' and tokens[i+2]['value']=='[')
    i=start+2;stack=[];pending=None;rows=[]
    while i<len(tokens):
        t=tokens[i];value=t['value']
        if value=='testGroup':
            if tokens[i+1]['kind']!='string':raise ValueError('Dynamic FB2 group')
            pending=hs_string(tokens[i+1]['value']);i+=2;continue
        if value=='[':stack.append(pending);pending=None
        elif value==']':
            if not stack:raise ValueError('Unbalanced FB2 registration list')
            stack.pop()
            if not stack:break
        elif t['kind']=='string' and tokens[i+1]['value']=='=:':
            end=next(j for j in range(i+2,len(tokens)) if tokens[j]['value']=='=?>')
            if tokens[end+1]['value']!='fb2' or tokens[end+2]['kind']!='string':raise ValueError('Unsupported FB2 expected expression')
            rows.append(dict(name=hs_string(value),groups=[g for g in stack if g],input_expression=text[tokens[i+2]['start']:tokens[end]['start']].strip(),
                             expected_inner=hs_string(tokens[end+2]['value']),registration_line=text.count('\n',0,t['start'])+1))
            i=end+3;continue
        i+=1
    if len(rows)!=3:raise ValueError('Original FB2 writer census differs')
    return rows


def runtime_inventory_lexer(text):
    # Reuse the accepted comment/string-aware Haskell lexer, not name grep.
    spec=importlib.util.spec_from_file_location('fb2_source_census',ROOT/'tools/census-test-inventory.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module.lex_haskell(text)


def original_writer_template(text):
    region=text[text.index('fb2 x ='):text.index('infix 4 =:')]
    tokens=runtime_inventory_lexer(region)
    values=[t['value'] for t in tokens]
    if len(tokens)!=10 or values[:3]!=['fb2','x','='] or values[4]!='++' or values[6:9]!=['++','x','++'] or any(tokens[i]['kind']!='string' for i in (3,5,9)):
        raise ValueError('Original FB2 formatter template differs')
    return hs_string(values[3])+hs_string(values[5]),hs_string(values[9])


def kotlin_writer_cases(text):
    prefix_region=text[text.index('val prefix ='):text.index('val cases =')]
    strings=re.findall(r'"(?:[^"\\]|\\.)*"',prefix_region)
    if len(strings)!=2:raise ValueError('Kotlin FB2 expected prefix differs')
    prefix=''.join(json.loads(s) for s in strings)
    region=text[text.index('val cases ='):text.index('return cases.map')]
    rows=[]
    for m in re.finditer(r'^            "([^"]+)" to \((.*?)\s+to\s*("(?:[^"\\]|\\.)*")\),?',region,re.M|re.S):
        rows.append(dict(name=m[1],input_expression=m[2],expected_inner=json.loads(m[3])))
    if len(rows)!=3:raise ValueError('Kotlin FB2 writer case census differs')
    return prefix,rows



def input_contract(original,kotlin,label):
    source_tokens=[t['value'] for t in runtime_inventory_lexer(original)]
    token_pattern=r'"(?:[^"\\]|\\.)*"|[A-Za-z_][A-Za-z0-9_]*|[().,{}]'
    ktokens=re.findall(token_pattern,kotlin)
    if re.sub(token_pattern,'',kotlin).strip():raise ValueError('Unsupported Kotlin builder input syntax')
    contracts={
      'para':(['para','"Lorem ipsum cetera."'],'Builder.para(Builder.text("Lorem ipsum cetera."))','Para[text Lorem ipsum cetera.]'),
      'Emphasis':(['para','(','emph','"emphasized"',')'],'Builder.para(Builder.emph(Builder.text("emphasized")))','Para[Emph[text emphasized]]'),
      'bullet list':(['bulletList','[','plain','$','text','"first"',',','plain','$','text','"second"',',','plain','$','text','"third"',']'],'Builder.bulletList(listOf("first", "second", "third").map { Builder.plain(Builder.text(it)) })','BulletList[[Plain[text first]],[Plain[text second]],[Plain[text third]]]')}
    if label not in contracts:raise ValueError('Unknown source builder input')
    source_expected,kotlin_expected,shape=contracts[label]
    if source_tokens!=source_expected or ktokens!=re.findall(token_pattern,kotlin_expected):raise ValueError('Original/Kotlin FB2 input construction differs')
    return dict(original=original,kotlin=kotlin,typed_shape=shape,original_builder=source('upstream/pandoc-types/src/Text/Pandoc/Builder.hs'),kotlin_builder=source('core/src/main/kotlin/io/heapy/kotdoc/ast/Builder.kt'),semantics='Original Blocks toPandoc=doc; Kotlin Builder.doc. Literal/text/para/emph/plain/bulletList constructors checked explicitly for these three finite inputs.')

def require(text,parts):
    for part in parts:
        if part not in text:raise ValueError('FB2 assertion/comparator contract missing: '+part)


def select_runtime(capture,components):
    records=runtime.normalize_paths(capture['paths'],capture['names'])
    selected=[r for r in records if r['components']==components]
    if len(selected)!=1:raise ValueError('FB2 runtime leaf missing/duplicate: '+str(components))
    return selected[0]


def draft_inventory():
    capture=json.loads((ROOT/'docs/test-inventory-runtime.json').read_text())
    runtime.validate_profile(capture['profile'])
    if capture['original_sources']!=runtime.original_sources():raise ValueError('FB2 original source/hash drift')
    rtext=(ROOT/READER).read_text();wtext=(ROOT/WRITER).read_text();helpers=(ROOT/HELPERS).read_text()
    rk=(ROOT/(TEST+'FB2ReaderTest.kt')).read_text();wk=(ROOT/(TEST+'FB2WriterTest.kt')).read_text()
    require(rtext,['goldenVsString name native','fromStringLazy . filter (/=\'\\r\') . unpack . fb2ToNative . toText','writerTemplate = Just mempty','purely (readFB2 def)','replaceExtension path ".native"'])
    require(helpers,["actual' = lines $ toString $ fn input","expected' = lines $ toString expected",'instance ToString String where\n  toString = id','instance ToPandoc Blocks where\n  toPandoc = doc'])
    require(wtext,['(=:) = test (purely (writeFB2 def) . toPandoc)'])
    require(rk,['fun `all original FB2 reader golden tests`','assertEquals(NativeReader.read(expected), document)','assertEquals(expected, NativeWriter.write(document, standalone = true).replace("\\r", ""))','readText().removePrefix("\\uFEFF")','readText().replace("\\r", "")'])
    require(wk,['fun `original FB2 writer examples`','assertEquals(prefix + pair.second + "</section></body></FictionBook>\\n", FB2Writer.write(Builder.doc(pair.first)))'])
    names=re.search(r'listOf\(("emphasis"[^\n]+)\)\.map \{ name ->',rk)
    if not names:raise ValueError('Kotlin reader case list changed')
    kotlin_reader_names=json.loads('['+names[1]+']')
    reader_pairs=re.findall(r'fb2Test "([^"]+)" "([^"]+)"',rtext)
    if len(reader_pairs)!=6 or [n.lower() for n,_ in reader_pairs]!=kotlin_reader_names:raise ValueError('Reader source/Kotlin case reconciliation differs')
    rows=[]
    for ordinal,(name,path) in enumerate(reader_pairs,1):
        original_input='upstream/pandoc/test/'+path;golden=str(Path(original_input).with_suffix('.native'))
        raw=(ROOT/golden).read_bytes()
        if b'\r' in raw or raw.startswith(b'\xef\xbb\xbf'):raise ValueError('Golden CR/BOM changes comparator equivalence; review required')
        (ROOT/original_input).read_bytes().decode('utf8')
        leaf=select_runtime(capture,['pandoc tests','Readers','FB2',name])
        rows.append(dict(**leaf,kind='mapped-fb2-unit-reader',source=source(READER),input=source(original_input),expectation_origin=source(golden),
                         original_comparator='goldenVsString exact bytes; actual: strict UTF8 toText(drop BOM/filter CR), readFB2 def, writeNative def{writerTemplate=Just mempty}, filter CR, UTF8 encode. Expected original .native bytes unchanged.',
                         kotlin_comparator='AST equals NativeReader.read(original golden); full NativeWriter.write(document,standalone=true) equals original golden, with CR removal on expected/actual. All six current original goldens contain no CR/BOM, so expected bytes are unchanged.',
                         kotlin=dict(source=source(TEST+'FB2ReaderTest.kt'),class_name='io.heapy.kotdoc.formats.FB2ReaderTest',method='all original FB2 reader golden tests',display_name=name.lower(),dynamic_ordinal=ordinal,
                                     junit_selector='io.heapy.kotdoc.formats.FB2ReaderTest#all original FB2 reader golden tests()',command="./gradlew :core:test --tests 'io.heapy.kotdoc.formats.FB2ReaderTest.all original FB2 reader golden tests'"),
                         production=[source(MAIN+p+'.kt') for p in ('FB2Reader','NativeReader','NativeWriter')],status='partial',
                         gap='Source/fixture/assertion bridge proved; coordinated fresh selected JUnit result capture pending. This unit leaf does not map other FB2-related CLI/ImageSize/Old leaves.'))
    prefix,suffix=original_writer_template(wtext);kprefix,kcases=kotlin_writer_cases(wk)
    if prefix!=kprefix or suffix!='</section></body></FictionBook>':raise ValueError('Independent FB2 writer expected template mismatch')
    aliases={'para':'paragraph','Emphasis':'emphasis','bullet list':'bullet list'}
    original_cases=original_writer_cases(wtext)
    for ordinal,(row,kcase) in enumerate(zip(original_cases,kcases),1):
        if aliases.get(row['name'])!=kcase['name'] or row['expected_inner']!=kcase['expected_inner']:raise ValueError('FB2 writer literal/alias mismatch')
        leaf=select_runtime(capture,['pandoc tests','Writers','FB2']+row['groups']+[row['name']])
        expected=prefix+row['expected_inner']+suffix
        rows.append(dict(**leaf,kind='mapped-fb2-unit-writer',source=source(WRITER),source_registration=row,
                         expectation_origin=dict(source=source(WRITER),template='fb2',expected_utf8_sha256=hashlib.sha256(expected.encode()).hexdigest(),origin='Original Haskell literal formatter and expected inner XML, independently compared to Kotlin literal expressions.'),
                         original_comparator='Tests.Helpers.test: lines(toString(purely(writeFB2 def)(toPandoc input))) == lines(toString expected); String ToString is identity. A terminal LF is ignored; interior lines remain significant.',
                         kotlin_comparator='Full XML equality to independently translated original prefix/inner/suffix plus one final LF; stronger terminal-newline constraint than original line comparator.',
                         alias=dict(source_label=row['name'],kotlin_label=kcase['name'],explicit=True),input_bridge=input_contract(row['input_expression'],kcase['input_expression'],row['name']),
                         kotlin=dict(source=source(TEST+'FB2WriterTest.kt'),class_name='io.heapy.kotdoc.formats.FB2WriterTest',method='original FB2 writer examples',display_name=kcase['name'],dynamic_ordinal=ordinal,
                                     junit_selector='io.heapy.kotdoc.formats.FB2WriterTest#original FB2 writer examples()',command="./gradlew :core:test --tests 'io.heapy.kotdoc.formats.FB2WriterTest.original FB2 writer examples'"),
                         production=[source(MAIN+'FB2Writer.kt'),source('core/src/main/kotlin/io/heapy/kotdoc/ast/Builder.kt')],status='partial',
                         gap='Source predicate/helper/literal and executable bridge proved; coordinated fresh selected JUnit result capture pending. Default option/global writer/container coverage remains separate.'))
    if len(rows)!=9 or len({r['id'] for r in rows})!=9:raise ValueError('Nine FB2 unit runtime IDs differ')
    # Other obligations reference the accepted source inventory, never inherit the unit mappings.
    snapshot=json.loads((ROOT/'docs/test-inventory.json').read_text());pending=[]
    old=[r for r in snapshot['pandoc_old'] if r['groups']==['fb2']]
    if len(old)!=8:raise ValueError('Eight additional FB2 Old obligations differ')
    for row in old:pending.append(dict(id=row['id'],runtime=select_runtime(capture,['pandoc tests','Old','fb2',row['name']]),kind='old-cli-golden',source=row['source'],input=row['input'],expectation_origin=row['expectation_origin'],status='unmapped',kotlin=None,gap='Separate CLI/options/resources/normalizer behavior; not discharged by nine unit mappings.'))
    command=[r for r in snapshot['pandoc_command'] if r['id']=='pandoc/command/figures-fb2.md/#1']
    if len(command)!=1:raise ValueError('FB2 Command obligation differs')
    pending.append(dict(id=command[0]['id'],runtime=select_runtime(capture,['pandoc tests','Command:','figures-fb2.md','#1']),kind='command-golden',source=command[0]['source'],expectation_origin=command[0]['expectation_origin'],code_sha256=command[0]['code_sha256'],expected_stdout=command[0]['expected_stdout'],expected_stderr=command[0]['expected_stderr'],expected_exit_status=command[0]['expected_exit_status'],status='unmapped',kotlin=None,gap='Command stdout/stderr/status and CLI behavior remain independently required.'))
    image=select_runtime(capture,['pandoc tests','ImageSize','imageSize','fixtures','fb2/test-small.png'])
    pending.append(dict(**image,kind='image-size-fixture',source=source('upstream/pandoc/test/Tests/ImageSize.hs'),input=source('upstream/pandoc/test/fb2/test-small.png'),expectation_origin='Original imageSize def @?= Right(ImageSize48 32 71 71), a separate predicate.',status='unmapped',kotlin=None,gap='ImageSize fixture obligation is separate from FB2 reader/writer unit behavior.'))
    return dict(schema_version=1,unit_leaves=rows,other_pending=pending,provenance=[source(READER),source(WRITER),source(HELPERS),source('upstream/pandoc/src/Text/Pandoc/UTF8.hs'),source('docs/test-inventory-runtime.json')],
                runtime_profile=capture['profile'],execution_result=None,
                scope='Draft source-backed nine FB2 unit-leaf bridge; coordinated selected JUnit evidence pending. Ten additional known FB2-related obligations remain unmapped; no all-FB2/full-port completion claim.')


EXECUTION='docs/test-inventory-fb2-execution.json'


# Review authority is this versioned code, not mutable evidence JSON. These
# fixed acceptance pins were independently derived from the actual nine-test
# JAR, accepted PSS core JAR bd788848..., and accepted5e968eea Git source blobs.
# Command/dependency/tool identities bind the actual recorded isolated run.
# Updating a pin requires a new artifact/provenance review; transport self-hashes
# below are not signatures, secret authentication, or authority to accept a run.
REVIEWED_CONSOLE_PINS = {
    "test_jar": "ea788511b8378f767d292af77cb42d8a5db370155b1926eb9b3d5174b1f58a47",
    "core_classes": "f84b7e9e858f476fdf8e4849e223bdf51dcb5fecc45f8cf17fbee7a3c8c0689f",
    "selected_classes": "1c590bdb0f292f742e865321b6b5eaa2da2a7cec566aa35bec216e13ee8b5e59",
    "core_sources": "2100096831e4ee4b89b521b260a871ca5db12b41591dd442e49f7e7a017c6bea",
    "test_sources": "a062d25c4680c5b966d6b4a96e9a795bee5bc050d9aff3ce5fbf5a0d5fd5d0da",
    "compile_command": "0ec00d365106416fee10b0d02d1ed1911c3c37f1a79ba487c8e8fc99456f1acf",
    "run_command": "261a04db313cbd0c9c217154f451480b87dcde8b217e183f832e2b1772dfa416",
    "dependencies": "bbfaa36dcc43f046396c1f7e187d9c4526e8b32091eef487328a4c53889887b8",
    "toolchain": "fc73f24b8390523327d36b567136c1d5005a9e67045add35158e2c0de6820280"
}


def _canonical_digest(value):
    return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(',',':'),ensure_ascii=True).encode()).hexdigest()


def _reviewed_console_identity(evidence):
    core=evidence['core_package']
    actual=dict(test_jar=evidence['test_jar']['sha256'],
                core_classes=_canonical_digest(core['classes']),
                selected_classes=_canonical_digest(core['selected_classes']),
                core_sources=_canonical_digest(core['source_bindings']),
                test_sources=_canonical_digest(evidence['test_sources']),
                compile_command=_canonical_digest(evidence['compile_command']),
                run_command=_canonical_digest(evidence['run_command']),
                dependencies=_canonical_digest(evidence['dependencies']),
                toolchain=_canonical_digest(evidence['toolchain']))
    for name,expected in REVIEWED_CONSOLE_PINS.items():
        if actual[name]!=expected:raise ValueError('FB2 reviewed artifact/tool identity differs: '+name)


def _bound_json(binding):
    path=binding['path']
    if path not in ('docs/test-inventory-fb2-current-evidence.json','docs/test-inventory-fb2-history.json'):
        raise ValueError('Unexpected FB2 evidence path')
    raw=(ROOT/path).read_bytes()
    if hashlib.sha256(raw).hexdigest()!=binding['sha256']:raise ValueError('FB2 evidence hash drift')
    return json.loads(raw)


def _archive(record):
    raw=base64.b64decode(record['base64'],validate=True)
    if hashlib.sha256(raw).hexdigest()!=record['sha256']:raise ValueError('FB2 archived artifact hash drift')
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        names=archive.namelist()
        if len(names)!=len(set(names)):raise ValueError('Duplicate FB2 archive entry')
        return {name:archive.read(name) for name in names}


def _validate_console_evidence(saved,rows,evidence,history):
    if evidence.get('schema_version')!=1 or evidence.get('compile_exit')!=0 or evidence.get('run_exit')!=0:
        raise ValueError('FB2 isolated compile/run did not succeed')
    expected_sources={r['kotlin']['source']['path'] for r in rows}|{'core/src/test/kotlin/io/heapy/kotdoc/logging/LogAssertions.kt'}
    if set(evidence['test_sources'])!=expected_sources:raise ValueError('FB2 compiled test/helper scope differs')
    for path,digest in {**evidence['test_sources'],**evidence['fixtures_and_original_sources']}.items():
        if source(path)['sha256']!=digest:raise ValueError('FB2 evidence source/hash drift: '+path)
    expected_fixtures={READER,WRITER}|{f'upstream/pandoc/test/fb2/reader/{name}.{suffix}' for name in ('emphasis','titles','epigraph','poem','meta','notes') for suffix in ('fb2','native')}
    if set(evidence['fixtures_and_original_sources'])!=expected_fixtures:raise ValueError('FB2 fixture/source scope differs')
    run=evidence['run_command'];compile=evidence['compile_command']
    tools=evidence['toolchain']
    if run[0]!=tools['runtime']['path'] or compile[0]!=tools['compiler']['path'] or tools['runtime']['java_home']!=tools['compiler']['java_home'] or tools['runtime']['version']!='21.0.12+10-LTS' or tools['console']['version']!='1.11.4':
        raise ValueError('FB2 typed command/tool identity differs')
    if saved['actual_command_args']!=run:raise ValueError('FB2 actual command differs from retained evidence')
    selectors=[run[i+1] for i,t in enumerate(run[:-1]) if t=='--select-method']
    expected_selectors={r['kotlin']['class_name']+'#'+r['kotlin']['method'] for r in rows}
    if len(selectors)!=2 or set(selectors)!=expected_selectors or any(t.startswith('--select-') and t!='--select-method' or t.startswith('--scan-') for t in run):
        raise ValueError('Actual executed factory selectors missing/extra')
    if '--fail-if-no-tests' not in run or '-Xmx512m' not in run or '-Xss256k' not in run:raise ValueError('FB2 runner guards/limits differ')
    if set(t for t in compile if t.endswith('.kt'))!=expected_sources:raise ValueError('FB2 compile source scope differs')
    test_path=compile[compile.index('-d')+1]
    cp=run[run.index('--class-path')+1].split(':')
    core=evidence['core_package'];dependencies=evidence['dependencies']
    core_paths=[name for name,digest in dependencies.items() if digest==core['sha256']]
    if len(core_paths)!=1 or cp[0]!=test_path or cp[1]!=core_paths[0] or core_paths[0] not in compile[compile.index('-cp')+1].split(':'):
        raise ValueError('FB2 immutable core/test classpath binding differs')
    if any(path not in dependencies for path in cp[1:]) or any(not path.endswith('.jar') or 'core/build' in path or '/build/classes/' in path for path in cp):raise ValueError('Mutable/non-JAR FB2 execution classpath')
    console=run[run.index('-jar')+1]
    if console!=tools['console']['path'] or dependencies.get(console)!=tools['console']['sha256']:
        raise ValueError('FB2 typed JUnit tool identity differs')
    if console not in dependencies or not console.endswith('kotdoc-junit-platform-console-1.11.4.jar'):raise ValueError('FB2 JUnit runner binding differs')
    if core['sha256']!='bd78884871b505c46617fe6021122134bfc526d1c0fe356d683cff34c8e1cf2a' or core['sha256']!=core['package_verification']['jarSha256'] or core['accepted_commit']!='5e968eea11f0c58013415acafe060f43dada9b62':
        # The full commit identity below is supplied by the accepted evidence, not a branch name.
        raise ValueError('FB2 accepted package identity differs')
    for path,digest in core['source_bindings'].items():
        if not path.startswith('core/src/main/kotlin/') or source(path)['sha256']!=digest:raise ValueError('FB2 accepted core source/hash drift: '+path)
    if len(core['source_bindings'])!=486:raise ValueError('FB2 accepted core source scope differs')
    selected=_archive(core['selected_class_zip'])
    if set(selected)!=set(core['selected_classes']):raise ValueError('FB2 production class archive scope differs')
    for name,data in selected.items():
        digest=hashlib.sha256(data).hexdigest()
        if digest!=core['selected_classes'][name] or digest!=core['classes'].get(name):raise ValueError('FB2 production class identity differs')
    if len(selected)!=54:raise ValueError('FB2 selected class family count differs')
    tests=_archive(evidence['test_jar']);classes=[name for name in tests if name.endswith('.class')]
    if not classes or any(not any(name.startswith(prefix) for prefix in ('io/heapy/kotdoc/formats/FB2ReaderTest','io/heapy/kotdoc/formats/FB2WriterTest','io/heapy/kotdoc/logging/LogAssertionsKt')) for name in classes):
        raise ValueError('FB2 isolated test artifact contains other source families')
    raw=evidence['raw_jupiter_report']['xml']
    if hashlib.sha256(raw.encode()).hexdigest()!=evidence['raw_jupiter_report']['sha256']:raise ValueError('FB2 raw XML hash drift')
    report=saved['reports'][0]
    if report['original_sha256']!=evidence['raw_jupiter_report']['sha256'] or re.sub(r' hostname="[^"]*"','',raw)!=report['xml_without_hostname']:
        raise ValueError('FB2 current raw report binding differs')
    expected={(r['id'],r['kotlin']['class_name'],r['kotlin']['method'],r['kotlin']['dynamic_ordinal'],r['kotlin']['display_name']) for r in rows}
    actual={(r['originalRuntimeID'],r['kotlinClass'],r['factory'],r['ordinal'],r['displayName']) for r in evidence['nine_results'] if r['result']=='PASS'}
    if len(evidence['nine_results'])!=9 or actual!=expected:raise ValueError('FB2 retained original nine-way join differs')
    old=history['old_execution_json']
    if hashlib.sha256(old.encode()).hexdigest()!=history['old_execution_json_sha256']:raise ValueError('FB2 historical execution hash drift')
    old=json.loads(old)
    old_reports={r['original_sha256'] for r in old['reports']}
    if len(history['raw_reports'])!=2 or {r['sha256'] for r in history['raw_reports']}!=old_reports or any(hashlib.sha256(r['xml'].encode()).hexdigest()!=r['sha256'] for r in history['raw_reports']):
        raise ValueError('FB2 historical XML drift')
    failed=history['later_failed_validation']
    if failed['exit']!=1 or failed['executed_inventory_tests']!=0 or 'Ran 0 tests' not in failed['output'] or 'Builder.kt' not in failed['output'] or hashlib.sha256(failed['output'].encode()).hexdigest()!=failed['output_sha256']:
        raise ValueError('FB2 historical failed check differs')
    _reviewed_console_identity(evidence)


def validate_execution(saved,rows):
    if saved.get('refresh_pending'):raise ValueError('FB2 execution refresh pending after Native integration')
    mode=saved.get('execution_mode','gradle')
    if (mode,saved['schema_version']) not in (('gradle',1),('junit-console',2)) or saved['fresh_execution'] is not True or saved['result']!=dict(tests=9,failures=0,errors=0,skips=0):raise ValueError('Nine-case execution result/mode differs')
    required={r['kotlin']['source']['path'] for r in rows}|{p['path'] for r in rows for p in r['production']}|{'core/build.gradle.kts'}
    if set(saved['relevant_sources_before_and_after'])!=required:raise ValueError('Executed source scope incomplete/unrelated')
    for path,expected in saved['relevant_sources_before_and_after'].items():
        if source(path)['sha256']!=expected:raise ValueError('Executed FB2 source/hash drift: '+path)
    if mode=='gradle':
        selectors={tokens[i+1] for tokens in [shlex.split(saved.get('actual_shared_command',''))] for i,t in enumerate(tokens[:-1]) if t=='--tests'}
        expected_selectors={r['kotlin']['class_name']+'.'+r['kotlin']['method'] for r in rows}
        if not expected_selectors<=selectors:raise ValueError('Actual executed factory selectors missing')
        expected={cls:[r['kotlin']['display_name'] for r in rows if r['kotlin']['class_name']==cls] for cls in {r['kotlin']['class_name'] for r in rows}}
    else:
        expected={(r['kotlin']['class_name'],r['kotlin']['method']+'()['+str(r['kotlin']['dynamic_ordinal'])+']') for r in rows}
        if len(saved['reports'])!=1:raise ValueError('Expected one current Jupiter engine report')
    observed={};console_cases=[]
    for report in saved['reports']:
        xml=report['xml_without_hostname']
        if hashlib.sha256(xml.encode()).hexdigest()!=report['xml_without_hostname_sha256']:raise ValueError('Captured XML hash drift')
        tree=ET.fromstring(xml);cls=tree.get('name');cases=tree.findall('testcase')
        if int(tree.get('tests','-1'))!=len(cases):raise ValueError('Executed factory case count differs')
        if any(tree.get(k)!='0' for k in ('failures','errors','skipped')) or any(x.find(tag) is not None for x in cases for tag in ('failure','error','skipped')):raise ValueError('FB2 execution failure/skip')
        if mode=='gradle':
            if cls not in expected or cls in observed:raise ValueError('Unexpected/duplicate executed test class')
            if [x.get('name') for x in cases]!=expected[cls] or any(x.get('classname')!=cls for x in cases):raise ValueError('Executed factory cases differ')
            observed[cls]=cases
        else:
            if cls!='JUnit Jupiter':raise ValueError('Unexpected FB2 engine report')
            console_cases.extend((c.get('classname'),c.get('name')) for c in cases)
    if mode=='gradle':
        if set(observed)!=set(expected):raise ValueError('Missing executed FB2 class')
    else:
        if len(console_cases)!=9 or set(console_cases)!=expected:raise ValueError('Executed factory cases differ')
        _validate_console_evidence(saved,rows,_bound_json(saved['evidence']),_bound_json(saved['history']))
    return saved


def inventory():
    data=draft_inventory()
    saved=validate_execution(json.loads((ROOT/EXECUTION).read_text()),data['unit_leaves'])
    for row in data['unit_leaves']:
        row['status']='mapped';row['execution_result']=source(EXECUTION)
        row['kotlin']['execution_identity']=dict(class_name=row['kotlin']['class_name'],display_name=row['kotlin']['display_name'],dynamic_ordinal=row['kotlin']['dynamic_ordinal'])
        row['gap']='Nine-case finite unit predicate/assertion linkage executed. Other CLI/Old/ImageSize obligations, global FB2 options/container/effect behavior and whole Pandoc/calibre remain separate.'
    data['execution_result']=dict(source=source(EXECUTION),result=saved['result'],fresh_execution=True,basis_root_commit=saved['basis_root_commit'],result_commit=saved['result_commit'])
    data['scope']='Exactly nine FB2 unit leaves mapped to original helpers/literals/goldens and fresh executed JUnit assertions. Ten other known FB2-related obligations remain unmapped; not complete FB2/Pandoc/global622.'
    return data


def apply_runtime_bridge(native,data):
    index={r['id']:r for r in data['unit_leaves']}
    if len(index)!=9:raise ValueError('Expected exactly nine FB2 unit links')
    found=set()
    for row in native['leaves']:
        if row['id'] in index:
            link=index[row['id']]
            if row['components']!=link['components']:raise ValueError('FB2 runtime components changed')
            row.update(status='mapped',kotlin=link['kotlin'],executable_mapping_ref='pandoc_fb2_unit_bridge/'+row['id'],gap=link['gap'])
            found.add(row['id'])
    if found!=set(index):raise ValueError('FB2 runtime mapping root omitted')
    return native



def execution_sources(data):
    paths={r['kotlin']['source']['path'] for r in data['unit_leaves']}|{p['path'] for r in data['unit_leaves'] for p in r['production']}|{'core/build.gradle.kts'}
    return {path:source(path)['sha256'] for path in sorted(paths)}


def freeze_execution(path):
    data=draft_inventory()
    saved=dict(schema_version=1,captured_epoch=time.time(),basis_root_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),sources=execution_sources(data))
    path.write_text(json.dumps(saved,indent=2)+'\n')
    return saved


def capture_execution(before_path,command_path,log_path,jdk):
    before=json.loads(before_path.read_text());data=draft_inventory()
    if before['sources']!=execution_sources(data):raise ValueError('FB2 relevant sources changed during execution')
    reports=[]
    for cls in ('FB2ReaderTest','FB2WriterTest'):
        path=ROOT/('core/build/test-results/test/TEST-io.heapy.kotdoc.formats.'+cls+'.xml')
        if path.stat().st_mtime<before['captured_epoch']:raise ValueError('Stale pre-freeze JUnit XML')
        raw=path.read_bytes();text=raw.decode()
        cleaned=re.sub(r' hostname="[^"]*"','',text)
        reports.append(dict(path=str(path.relative_to(ROOT)),original_sha256=hashlib.sha256(raw).hexdigest(),xml_without_hostname=cleaned,
                            xml_without_hostname_sha256=hashlib.sha256(cleaned.encode()).hexdigest(),transformation='Only hostname attribute removed; all case/result data preserved verbatim.'))
    log=log_path.read_bytes()
    if b'BUILD SUCCESSFUL' not in log:raise ValueError('Successful Gradle log required')
    saved=dict(schema_version=1,fresh_execution=True,refresh_pending=False,execution_session='coordinated post-Native integration Gradle',basis_root_commit=before['basis_root_commit'],result_commit=None,
               result_commit_semantics='Fresh worktree execution awaiting integration; basis commit is not attribution of concurrent WIP to that commit.',
               selected_reproduction_command="./gradlew :core:test --tests 'io.heapy.kotdoc.formats.FB2ReaderTest.all original FB2 reader golden tests' --tests 'io.heapy.kotdoc.formats.FB2WriterTest.original FB2 writer examples'",
               actual_shared_command=command_path.read_text().strip(),actual_command_provenance='Exact shared command supplied by executing worker; only the two FB2 factory XML reports counted here.',
               jdk=jdk,result=dict(tests=9,failures=0,errors=0,skips=0),relevant_sources_before_and_after=before['sources'],
               source_hash_semantics='Relevant8test/production/Gradle files checked before/after execution. Other Read/native suite counts are not FB2 coverage.',
               reports=reports,shared_run_log_sha256=hashlib.sha256(log).hexdigest())
    validate_execution(saved,data['unit_leaves'])
    (ROOT/EXECUTION).write_text(json.dumps(saved,ensure_ascii=False,indent=2)+'\n')
    return saved


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    action=parser.add_mutually_exclusive_group()
    action.add_argument('--freeze-execution-sources',type=Path)
    action.add_argument('--capture-execution',type=Path,metavar='BEFORE_JSON')
    parser.add_argument('--command-file',type=Path);parser.add_argument('--log',type=Path);parser.add_argument('--jdk')
    args=parser.parse_args()
    if args.freeze_execution_sources:
        freeze_execution(args.freeze_execution_sources);print('Frozen8relevant FB2source hashes; no tests executed')
    elif args.capture_execution:
        if not all((args.command_file,args.log,args.jdk)):parser.error('--capture-execution requires --command-file --log --jdk')
        capture_execution(args.capture_execution,args.command_file,args.log,args.jdk);print('Captured9fresh executed FB2unit assertions; other10stillunmapped')
    else:
        data=inventory();print('Verified9 executed FB2 unit mappings;',len(data['other_pending']),'other obligations unmapped; not allFB2')
