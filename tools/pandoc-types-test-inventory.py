#!/usr/bin/env python3
"""Join all original pandoc-types registrations to immutable accepted executable evidence."""
from collections import Counter
from functools import lru_cache
import gzip
import atexit
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT=Path(__file__).resolve().parents[1]
COMMIT='66722c107b7663ded9c8f510a41e05c3e131a970'
SHRINK='fde33c3346204ff8f2eee836ef445fd302a0bdc4'
VECTOR='a5c8fb8f8366b825db351057153b2f3160cfcdef'
PT='upstream/pandoc-types/test/test-pandoc-types.hs'
AST='core/src/test/kotlin/io/heapy/kotdoc/ast/'
RES='core/src/test/resources/pandoc-types/'
MANIFEST=RES+'remaining-property-manifest.json'


def sha(data):return hashlib.sha256(data).hexdigest()


_readers={}


def close_readers():
    for process in _readers.values():
        process.stdin.close();process.stdout.close();process.wait()
    _readers.clear()


atexit.register(close_readers)


@lru_cache(maxsize=None)
def pinned_submodule(commit,folder):
    return subprocess.check_output(['git','rev-parse',commit+':'+folder],cwd=ROOT,text=True).strip()


def git_object(repository,revision,path):
    if any(c in path+revision for c in ('\n','\r')):raise ValueError('Invalid Git object query')
    key=str(repository)
    if key not in _readers:
        _readers[key]=subprocess.Popen(['git','cat-file','--batch'],cwd=repository,stdin=subprocess.PIPE,stdout=subprocess.PIPE)
    process=_readers[key]
    process.stdin.write((revision+':'+path+'\n').encode());process.stdin.flush()
    header=process.stdout.readline().split()
    if len(header)!=3 or header[1]!=b'blob':raise ValueError('Historical artifact unavailable: '+path)
    value=process.stdout.read(int(header[2]))
    if process.stdout.read(1)!=b'\n':raise ValueError('Malformed Git blob stream')
    return value


@lru_cache(maxsize=None)
def blob(path,commit=COMMIT):
    for folder in ('upstream/pandoc-types','upstream/pandoc','upstream/calibre'):
        if path.startswith(folder+'/'):
            return git_object(ROOT/folder,pinned_submodule(commit,folder),path[len(folder)+1:])
    return git_object(ROOT,commit,path)


def artifact(path,commit=COMMIT):return dict(path=path,sha256=sha(blob(path,commit)),recorded_commit=commit)


def declaration(text,name,path=PT):
    starts=list(re.finditer(r'^([a-z][A-Za-z0-9_\']*)\b',text,re.M))
    match=next((m for m in starts if m[1]==name),None)
    if match is None:raise ValueError('Original declaration missing '+name)
    end=next((m.start() for m in starts if m.start()>match.start() and m[1]!=name),len(text))
    return dict(path=path,symbol=name,first_line=text.count('\n',0,match.start())+1,last_line=text.count('\n',0,end)+1,sha256=sha(text[match.start():end].encode()),recorded_commit=COMMIT)


def registrations(text):
    start=text.index('tests ::');region=text[start:];rows=[]
    for m in re.finditer(r'testProperty "([^"]+)"\s+([^\n]+)',region):
        name=m[1];rows.append(dict(id='pandoc-types/property/'+name,name=name,kind='source-property',expression=m[2].strip(),registration_line=text.count('\n',0,start+m.start())+1))
    for m in re.finditer(r'testEncodeDecode "([^"]+)"\s+(t_\w+)',region):
        rows.append(dict(id='pandoc-types/json/'+m[2],name=m[1],kind='paired-literal-example',expression=m[2],registration_line=text.count('\n',0,start+m.start())+1))
    for name in ('t_tableSan','t_tableNormExample'):
        if len(re.findall(r'^\s*, '+name+r'\s*$',region,re.M))!=1:raise ValueError('Original table literal registration differs')
        labels=re.findall(r'^'+re.escape(name)+r'\s*=\s*testCase\s+("(?:[^"\\]|\\.)*")',text,re.M)
        if len(labels)!=1:raise ValueError('Original table literal label missing/ambiguous')
        label=json.loads(labels[0])
        rows.append(dict(id='pandoc-types/literal/'+name,name=label,kind='literal-assertion',expression=name,registration_line=text.count('\n',0,start+region.index(', '+name))+1))
    if Counter(r['kind'] for r in rows)!=dict({'source-property':23,'paired-literal-example':56,'literal-assertion':2}):raise ValueError('Original registration census differs')
    if len({r['id'] for r in rows})!=81:raise ValueError('Duplicate original registration ID')
    return rows



def generator_instance(type_name):
    path='upstream/pandoc-types/src/Text/Pandoc/Arbitrary.hs';text=blob(path).decode()
    match=re.search(r'^instance Arbitrary '+re.escape(type_name)+r' where\n.*?(?=^instance |\Z)',text,re.M|re.S)
    if not match:raise ValueError('Original generator instance missing')
    return dict(path=path,type=type_name,first_line=text.count('\n',0,match.start())+1,sha256=sha(match[0].encode()),recorded_commit=COMMIT)

def validate_join(rows,mapping):
    if set(r['id'] for r in rows)!=set(mapping) or len(rows)!=81 or len(mapping)!=81:raise ValueError('81 manifest/source registration join differs')


def validate_sources(manifest,commit):
    for path,expected in manifest['sources'].items():
        if sha(blob(path,commit))!=expected:raise ValueError('Historical source hash differs: '+path)


def payload(name,manifest,commit=COMMIT):
    raw=gzip.decompress(blob(RES+name+'.jsonl.gz',commit))
    expected=manifest['payloads'][name]
    if sha(raw)!=expected:raise ValueError('Historical payload hash differs: '+name)
    return [json.loads(line) for line in raw.splitlines()]


def require(text,parts):
    for part in parts:
        if part not in text:raise ValueError('Executable assertion contract missing: '+part)


def kotlin_factory(cls,method,contract):
    path=AST+cls+'.kt';text=blob(path).decode()
    signature='fun `'+method+'`' if ' ' in method else 'fun '+method
    if text.count(signature)!=1:raise ValueError('Executable Kotlin method missing/ambiguous')
    require(text,contract)
    return dict(class_name='io.heapy.kotdoc.ast.'+cls,method=method,source=artifact(path),declaration_line=text.count('\n',0,text.index(signature))+1,
                junit_method_selector='io.heapy.kotdoc.ast.'+cls+'#'+method+'()',
                execution_command='./gradlew :core:test --tests io.heapy.kotdoc.ast.'+cls,
                command_role='Reproduction selector; inventory does not execute it or claim an archived historical shell invocation.',
                assertion_contract=contract)


def case(name,ordinal,line=None):
    return dict(display_name=name,dynamic_ordinal=ordinal,fixture_line=line,
                junit_factory_suffix='[dynamic-test:#'+str(ordinal)+']' if ordinal is not None else None)


def json_token(x):return json.dumps(x,ensure_ascii=False,separators=(',',':'))


def result_record(commit,count,needle):
    ledger=blob('docs/PORTING.md',commit).decode()
    if ledger.count(needle)!=1:raise ValueError('Historical executed result missing/ambiguous at commit')
    position=ledger.index(needle);excerpt=ledger[position:position+700]
    if not any(t in excerpt for t in ('no failures/errors/skips','zero failures/errors/skips')):raise ValueError('Historical clean result not documented near count')
    return dict(recorded_commit=commit,recorded_junit_tests=count,failures=0,errors=0,skips=0,
                evidence=artifact('docs/PORTING.md',commit),documented_result=excerpt,result_line=ledger.count('\n',0,position)+1,fresh_execution=False,
                limitation='Historical independently reviewed XML totals documented in immutable ledger. Raw per-case XML is not committed; dynamic identities below are reconstructed from accepted factory/fixture iteration, not claimed XML replays.')


def support_checkpoint(commit,manifest_path,count,needle):
    manifest=json.loads(blob(manifest_path,commit));validate_sources(manifest,commit)
    for name in manifest['payloads']:payload(name,manifest,commit)
    return dict(id='pandoc-types/support/'+commit,kind='historical-generator-shrinker-observations',result=result_record(commit,count,needle),manifest=artifact(manifest_path,commit),
                sources=[artifact(p,commit) for p in manifest['sources']],payloads=manifest['payloads'],
                gap='Finite support observations are not additional original test registrations, universal demand/seed fidelity, typed Read or complete public API coverage.')



def verify_original_exposures(original):
    path='tools/haskell-types-property-closure-reference/OriginalRemainingProperties.hs';module=blob(path).decode()
    def excerpt(a,b):return original[original.index(a):original.index(b)]
    header=module[:module.index('prop_roundtrip ::')]
    expected=header+'\n'.join(excerpt(a,b) for a,b in [('prop_roundtrip ::','testEncode ::'),('withWidth ::','t_tableNormExample ::'),('p_figureRepresentation ::','tests ::')])
    specs=[('widthNormIsIdempotent ::','p_tableNormHeadIdempotent ::','\\n a ->','sourceWidthNormIsIdempotent f n a'),('normIsSubset ::','p_tableNormHeadIsSubset ::','\\n a ->','sourceNormIsSubset f proj n a'),('testRowCorrectWidth ::','p_tableNormHeadRowWidth ::','\\n a ->','sourceRowCorrectWidth f proj n a'),('p_tableNormBodyIsSubset ::','p_tableNormFootIsSubset ::','\\n tb ->','sourceBodyIsSubset n tb'),('p_tableNormBodyRowWidth ::','p_tableNormFootRowWidth ::','\\n tb ->','sourceBodyRowWidth n tb'),('p_figureRepresentation ::','tests ::','\\figureCaption ->','sourceFigureRepresentation figureCaption')]
    for a,b,binder,name in specs:
        expected+='\n-- Mechanical exposure of the original forall body; body below is verbatim.\n'+name+' ='+excerpt(a,b).split(binder,1)[1]
    if module!=expected:raise ValueError('Original property exposure body differs')
    walk=blob('tools/haskell-types-properties-reference/TypesPropertiesReference.hs').decode()
    if excerpt('p_walk ::','prop_roundtrip ::').strip() not in walk:raise ValueError('Original Walk predicate/callback bodies differ')

@lru_cache(maxsize=1)
def inventory():
    original=blob(PT).decode();rows=registrations(original)
    verify_original_exposures(original)
    manifest=json.loads(blob(MANIFEST))
    if manifest['originalRegisteredCases']!=81 or manifest['kotlinAssertions']!=14316:raise ValueError('Historical manifest units differ')
    validate_join(rows,manifest['registrations']);validate_sources(manifest,COMMIT)
    remaining=payload('remaining-properties',manifest);reports=payload('remaining-property-suite',manifest)
    counts=Counter(r['kind'] for r in remaining)
    if counts!={'head':1500,'body':1500,'foot':1500,'figure':305,'json':500}:raise ValueError('Original generator observation domains differ')
    for kind in ('head','body','foot'):
        selected=[r for r in remaining if r['kind']==kind]
        if {(r['seed'],r['width']) for r in selected}!={(seed,width) for seed in range(1,101) for width in range(2,17)}:raise ValueError('Original width/seed domain differs')
        if not all(r[field] is True for r in selected for field in ('idempotent','subset','rowWidth')):raise ValueError('Original table predicate failed')
    if len(reports)!=11 or len({r['name'] for r in reports})!=11 or not all(r['success'] is True and r['tests']==1000 for r in reports):raise ValueError('Original suite reports differ')
    walk_manifest=json.loads(blob(RES+'properties-manifest.json'));validate_sources(walk_manifest,COMMIT)
    walk_raw=gzip.decompress(blob(RES+'properties.jsonl.gz'))
    if sha(walk_raw)!=walk_manifest['observationsSha256']:raise ValueError('Original Walk payload differs')
    walks=[json.loads(line) for line in walk_raw.splitlines()]
    if len(walks)!=500 or not all(r['originalPredicates'] is True for r in walks):raise ValueError('Original Walk observation domain differs')
    fixtures=json.loads(blob(RES+'json-fixtures.json'));fi={r['name']:r for r in fixtures}
    # Independent extraction of the literal bytes from the original test module.
    for symbol,f in fi.items():
        match=re.search(r'^'+symbol+r' :: \((\w+), ByteString\)\n(.*?)(?=^t_\w+ ::|^-- headers)',original,re.M|re.S)
        if not match or match[1]!=f['type'] or re.search(r'\[s\|(.*?)\|\]',match[2],re.S)[1]!=f['json']:raise ValueError('Original literal JSON differs')
    if len(fi)!=56:raise ValueError('Literal fixture census differs')
    result=result_record(COMMIT,32056,'**32,056 actual tests**')
    factories={
      'walk':kotlin_factory('OriginalTypesPropertiesTest','originalWalkPredicates',['@TestFactory fun originalWalkPredicates()', 'rows.flatMap', 'assertEquals(PandocJson.decode(r.getValue(name)), visitor.walk(d))','assertEquals(r.getValue(name), actual())','"p_walk $name $id"','"p_query $name $id"']),
      'table':kotlin_factory('OriginalTypesRemainingPropertiesTest','originalTablePredicates',['@TestFactory fun originalTablePredicates()', 'listOf("Idempotent" to "idempotent","IsSubset" to "subset","RowWidth" to "rowWidth")','"p_tableNorm$family$property width=$width seed=${wire.seed}"','assertEquals(native,predicate)','assertEquals(PandocJson.decodeTableBody(r.getValue("expected")),actual)']),
      'figure':kotlin_factory('OriginalTypesRemainingPropertiesTest','originalFigureRepresentation',['@TestFactory fun originalFigureRepresentation()', 'observations.filter { it.kind=="figure" }','assertEquals(r.getValue("expected").jsonArray.map(PandocJson::decodeBlock),actual)','assertTrue(native); assertEquals(native,actual==right)']),
      'json':kotlin_factory('OriginalTypesRemainingPropertiesTest','originalJsonRoundTrip',['@TestFactory fun originalJsonRoundTrip()', 'observations.filter { it.kind=="json" }','assertEquals(r.getValue("encoded").jsonPrimitive.content,wire)','assertEquals(PandocJson.decode(r.getValue("decoded")),decoded)','assertEquals(input,decoded)']),
      'literal-json':kotlin_factory('UpstreamJsonTest','upstream exact JSON encoding and typed decoding',['@TestFactory','fixtures().flatMap','"$name encoding"','assertEquals(raw, encode(type, value).toString())','"$name decoding"','assertEquals(value, decode(type, json))'])}
    walk_order=['inlineTrans','blockTrans','metaValueTrans','metaTrans','inlinesTrans','blocksTrans','inlineQuery','blockQuery','metaValueQuery','metaQuery','inlinesQuery','blocksQuery']
    table_rows=[(i,r) for i,r in enumerate(remaining,1) if r['kind'] in ('head','body','foot')]
    arbi='upstream/pandoc-types/src/Text/Pandoc/Arbitrary.hs'
    for row in rows:
        name=row['name'];family=None;oracle=None;helpers=[];generator=None
        if row['kind']=='source-property':
            if name.startswith(('p_walk','p_query')):
                op,callback=name.split(' ');factory='walk';position=walk_order.index(callback)
                identities=[case(('p_walk' if op.startswith('p_walk') else 'p_query')+' '+callback+' seed='+json_token(r['seed'])+' size='+json_token(r['size']),12*(i-1)+position+1,i) for i,r in enumerate(walks,1)]
                predicate=[op,callback];generator='Pandoc'
                oracle=dict(source=artifact('tools/haskell-types-properties-reference/TypesPropertiesReference.hs'),expectation='Independent Data.Generics.everywhere/everything on original generated Pandoc; original foldr/tails adapters and original text casing retained.',fixture=artifact(RES+'properties.jsonl.gz'),expected_field=callback)
                production=[('PandocVisitor.kt',['walk'])]
            elif name.startswith('p_tableNorm'):
                match=re.fullmatch(r'p_tableNorm(Head|Body|Foot)(Idempotent|IsSubset|RowWidth)',name)
                if not match:raise ValueError('Unknown table property')
                family,prop=match.groups();kind=family.lower();factory='table';position=['Idempotent','IsSubset','RowWidth'].index(prop)
                identities=[case(name+' width='+str(r['width'])+' seed='+json_token(r['seed']),3*(i-1)+position+1,line) for i,(line,r) in enumerate(table_rows,1) if r['kind']==kind]
                predicate=[name];helpers=['withWidth','widthNormIsIdempotent','cellSubset','cellsSubsetPad','rowSubset','rowsSubset','normIsSubset','firstRowCorrectWidth','testRowCorrectWidth'];generator='Table'+family
                oracle=dict(source=artifact('tools/haskell-types-property-closure-reference/OriginalRemainingProperties.hs'),runner=artifact('tools/haskell-types-property-closure-reference/TypesPropertyClosureReference.hs'),expectation='Verbatim original predicate/forall bodies plus independent complete Builder normalization outputs; Kotlin checks expected outputs before translated predicate.',fixture=artifact(RES+'remaining-properties.jsonl.gz'),record_kind=kind,expected_fields=['expected',dict(Idempotent='idempotent',IsSubset='subset',RowWidth='rowWidth')[prop]])
                production=[('Tables.kt',['normalize'+family])]
            elif name=='round-trip':
                factory='json';predicate=['prop_roundtrip'];generator='Pandoc'
                selected=[(line,r) for line,r in enumerate(remaining,1) if r['kind']=='json']
                identities=[case('round-trip seed='+json_token(r['seed'])+' size='+json_token(r['size']),i,line) for i,(line,r) in enumerate(selected,1)]
                oracle=dict(source=artifact('tools/haskell-types-property-closure-reference/OriginalRemainingProperties.hs'),runner=artifact('tools/haskell-types-property-closure-reference/TypesPropertyClosureReference.hs'),fixture=artifact(RES+'remaining-properties.jsonl.gz'),record_kind='json',expected_fields=['encoded','decoded','predicate'],expectation='Independent original Aeson bytes/decoder tree and original predicate; not Kotlin self-roundtrip alone.')
                production=[('PandocJson.kt',['encode','decode'])]
            elif name=='p_figureRepresentation figure representation':
                factory='figure';predicate=['p_figureRepresentation'];generator='[Inline] via original Inline instance and QuickCheck list generator'
                selected=[(line,r) for line,r in enumerate(remaining,1) if r['kind']=='figure']
                identities=[case(name+' seed='+json_token(r['seed'])+' size='+json_token(r['size'])+' witness='+json_token(r['witness']),i,line) for i,(line,r) in enumerate(selected,1)]
                oracle=dict(source=artifact('tools/haskell-types-property-closure-reference/OriginalRemainingProperties.hs'),runner=artifact('tools/haskell-types-property-closure-reference/TypesPropertyClosureReference.hs'),fixture=artifact(RES+'remaining-properties.jsonl.gz'),record_kind='figure',expected_fields=['expected','predicate'],expectation='Independent original Builder complete output and original Figure predicate, with generated captions and five explicit witnesses.')
                production=[('Builder.kt',['simpleFigureWith'])]
            else:raise ValueError('Unmapped original property')
            row['source_predicate']=[declaration(original,p) for p in predicate+helpers]
            row['original_generator']=dict(type=generator,instance=generator_instance(generator if generator in ('Pandoc','TableHead','TableBody','TableFoot') else 'Inline'),source=artifact(arbi),quickcheck=artifact('vendor/quickcheck-2.16.0.0/Arbitrary.hs'),domain='Exact finite original-generated fixture domain only; no universal seed/size/demand claim.')
        elif row['kind']=='paired-literal-example':
            factory='literal-json';symbol=row['expression'];f=fi[symbol];index=next(i for i,x in enumerate(fixtures) if x['name']==symbol)
            identities=[case(symbol+' '+direction,2*index+offset,index+1) for direction,offset in [('encoding',1),('decoding',2)]]
            typed=blob(AST+'UpstreamJsonTest.kt').decode();match=re.search(r'^        "'+symbol+r'" to .*?(?=^        "t_\w+" to|^    \))',typed,re.M|re.S)
            if not match:raise ValueError('Independent typed literal expression missing')
            row['source_predicate']=[declaration(original,p) for p in ('testEncode','testDecode','testEncodeDecode')]
            oracle=dict(fixture=artifact(RES+'json-fixtures.json'),symbol=symbol,type=f['type'],original_literal=declaration(original,symbol),expected_json_sha256=sha(f['json'].encode()),typed_expectation_source=artifact(AST+'UpstreamJsonTest.kt'),typed_expression_sha256=sha(match[0].encode()),expectation='Original literal JSON bytes plus separately translated typed expected object map; expected object is not decoded from fixture JSON.')
            type=f['type'];codec={'Meta':'Meta','MetaValue':'MetaValue','QuoteType':'Enum','CitationMode':'Enum','MathType':'Enum','Citation':'Citation','Inline':'Inline','Block':'Block','Row':'Row','Caption':'Caption','TableHead':'TableHead','TableBody':'TableBody','TableFoot':'TableFoot','Cell':'Cell','RowHeadColumns':'TableBody','RowSpan':'Cell','ColSpan':'Cell'}[type]
            production=[('PandocJson.kt',['encode'+codec,'decode'+codec])]
        else:
            symbol=row['expression'];method={'t_tableSan':'upstream table sanitisation','t_tableNormExample':'upstream table normalization example'}[symbol]
            factory=symbol;factories[factory]=kotlin_factory('TablesTest',method,['assertEquals(expected, result)' if symbol=='t_tableSan' else 'assertEquals(expected, Tables.normalize(initial))'])
            identities=[case(method,None)];row['source_predicate']=[declaration(original,symbol)]
            oracle=dict(original_literal=declaration(original,symbol),typed_expectation_source=artifact(AST+'TablesTest.kt'),expectation='Independent original expected table literal translated explicitly in Kotlin; original expected values unchanged.')
            production=[('Tables.kt',['simpleTable'] if symbol=='t_tableSan' else ['normalize'])]
        row.update(source=artifact(PT),status='mapped',mapping_scope='Executable finite source assertion/predicate evidence only; not complete semantic API/generator/runtime acceptance.',original_generator=row.get('original_generator'),
                   kotlin=dict(factory_ref=factory,**factories[factory],identities=identities,identities_sha256=sha(json.dumps(identities,ensure_ascii=False,separators=(',',':')).encode()),identity_origin='Reconstructed from exact accepted factory source/fixture iteration; display names plus actual factory dynamic ordinals, not inferred from manifest name similarity.'),
                   production=[dict(source=artifact(AST.replace('/test/','/main/')+file),entrypoints=entries) for file,entries in production],
                   expectation_origin=oracle,result_ref=COMMIT,manifest_mapping=manifest['registrations'][row['id']],
                   gap='Finite executed linkage discharged. Universal generator/shrinker semantics, Read/ordering, public Walk/Builder/Generic/API/effects and the whole Pandoc/calibre port remain separate requirements.')
        expected=manifest['registrations'][row['id']]
        expected_method=factories[factory]['class_name'].rsplit('.',1)[1]+'.'+factories[factory]['method']
        if row['kind']=='source-property':expected_method+=': '+name.replace('p_walkList','p_walk').replace('p_queryList','p_query')+' *'
        elif row['kind']=='paired-literal-example':expected_method+=': '+row['expression']+' encoding/decoding'
        if expected_method!=expected:raise ValueError('Manifest executable factory/identity differs')
        for prod in row['production']:
            text=blob(prod['source']['path']).decode()
            for symbol in prod['entrypoints']:
                if not re.search(r'\bfun[^\n]*\b'+re.escape(symbol)+r'\s*\(',text):raise ValueError('Historical production function missing '+symbol)
    if sum(len(r['kotlin']['identities']) for r in rows)!=20419:raise ValueError('Finite mapped behavior count differs')
    checkout={}
    for row in rows:
        artifacts=[row['kotlin']['source'],*[p['source'] for p in row['production']]]
        if 'fixture' in row['expectation_origin']:artifacts.append(row['expectation_origin']['fixture'])
        for item in artifacts:
            if sha((ROOT/item['path']).read_bytes())!=item['sha256']:raise ValueError('Mapped executable/fixture checkout differs from accepted artifact: '+item['path'])
            checkout[item['path']]=item

    return dict(registrations=sorted(rows,key=lambda r:r['id']),manifest=artifact(MANIFEST),verified_manifest_sources=[artifact(p) for p in manifest['sources']],
                original_registrations=81,assertion_property_registrations=137,finite_kotlin_behavior_identities=20419,
                original_suite_reports=dict(count=11,trials_per_property=1000,fixture=artifact(RES+'remaining-property-suite.jsonl.gz'),role='Eleven Kotlin checks of native QuickCheck reports supplement behavior assertions; 11000 native trials are not Kotlin conversions or new source properties.'),
                source_exposure_verification='Original eleven property excerpts/six forall exposures and twelve Walk predicate/callback bodies mechanically verified against pinned source; independent oracle outputs retain original generators.',mapped_checkout_matches=[checkout[p] for p in sorted(checkout)],result=result,support_checkpoints=[support_checkpoint(SHRINK,RES+'shrink-closure-manifest.json',33284,'33,284 actual'),support_checkpoint(VECTOR,RES+'vector-manifest.json',33466,'33,466 actual')],
                fresh_execution=False,gap='All81 source registration links now explicit finite historical evidence. Read WIP excluded; full generator/runtime/public API and global622 mapping remain open.')


if __name__=='__main__':
    result=inventory();print('Verified81 historical executable mappings;137 assertion/property registrations;',result['finite_kotlin_behavior_identities'],'finite behavior identities; no freshJUnit')
