#!/usr/bin/env python3
"""Dependency root reconciliation. Registration units never imply assertion coverage."""
import argparse
from collections import Counter
import hashlib
import gzip
import xml.etree.ElementTree as ET
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
CAPTURE=ROOT/'docs/test-inventory-dependency-cabal.json'
OBSERVER='tools/test-inventory-dependency-reference/CabalInventory.hs'
FREEZE='tools/haskell-html-reader-reference/cabal.project.freeze'
spec=importlib.util.spec_from_file_location('dependency_census',ROOT/'tools/census-test-inventory.py')
census=importlib.util.module_from_spec(spec);spec.loader.exec_module(census)


def source(path):
    p=Path(path)
    return dict(path=str(p),sha256=hashlib.sha256((ROOT/p).read_bytes()).hexdigest())


def cabal_paths():
    return sorted(str(p.relative_to(ROOT)) for p in (ROOT/'vendor').rglob('*.cabal'))


def declared_names(text,kind='test-suite'):
    # Independent syntax-level reconciliation against original Cabal's AST. Cabal
    # rejects malformed declarations; comments cannot register components here.
    return re.findall(r'^\s*'+re.escape(kind)+r'\s+([A-Za-z0-9_.+-]+)\s*(?:--[^\n]*)?$',text,re.M|re.I)


def validate_capture(value):
    if value['observer']!=source(OBSERVER):raise ValueError('Dependency observer source drift')
    if value['parser']!='Cabal-3.14.1.0' or value['compiler']!='9.12.2':raise ValueError('Unexpected dependency parser profile')
    paths=cabal_paths()
    if value['sources']!=[source(p) for p in paths]:raise ValueError('Dependency Cabal source inventory/hash drift')
    if [r['path'] for r in value['packages']]!=paths:raise ValueError('Omitted/duplicated Cabal package root')
    for row in value['packages']:
        text=(ROOT/row['path']).read_text()
        for key,kind in [('test_suites','test-suite'),('executables','executable')]:
            expected=declared_names(text,kind)
            actual=[s['name'] for s in row[key]]
            if Counter(actual)!=Counter(expected) or len(set(actual))!=len(actual):raise ValueError('Cabal declaration reconciliation failed: '+row['path']+' '+kind)
    return value


def capture(binary,write=False):
    version=subprocess.check_output(['/tmp/kotdoc-haskell/ghc/bin/ghc-pkg','field','Cabal','version','--simple-output'],text=True).strip()
    compiler=subprocess.check_output(['/tmp/kotdoc-haskell/ghc/bin/ghc','--numeric-version'],text=True).strip()
    paths=cabal_paths()
    packages=json.loads(subprocess.check_output([str(binary),*paths],cwd=ROOT))
    result=dict(schema_version=1,parser='Cabal-'+version,compiler=compiler,observer=source(OBSERVER),sources=[source(p) for p in paths],packages=packages,
                semantics='Original Cabal GenericPackageDescription parser resolves common stanza imports and retains every conditional branch. No finalization/profile selection, package build, test listing or assertion execution.',
                counting_unit='Declared suite or executable roots, never runtime tests/assertions.',test_assertions_executed=False)
    validate_capture(result)
    if write:CAPTURE.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    return result


def nodes(tree,conditions=()):
    yield conditions,tree['data']
    for branch in tree['branches']:
        yield from nodes(branch['then'],conditions+(branch['condition'],))
        if branch['else'] is not None:yield from nodes(branch['else'],conditions+('NOT ('+branch['condition']+')',))


def resolve_paths(base,dirs,value,module=False):
    if not dirs:dirs=['.']
    suffixes=['.hs','.lhs','.hsc'] if module else ['']
    value=value.replace('.','/') if module else value
    paths=[]
    for directory in sorted(set(dirs)):
        for suffix in suffixes:
            path=base/directory/(value+suffix)
            # Never inspect files outside the retained package, including ../.
            if not path.resolve().is_relative_to(base.resolve()):
                paths.append(dict(path=str(path.relative_to(ROOT)),availability='outside-retained-package',sha256=None));continue
            paths.append(dict(path=str(path.relative_to(ROOT)),availability='available' if path.is_file() else 'missing-source',sha256=source(path.relative_to(ROOT))['sha256'] if path.is_file() else None))
    return paths


def suite_record(package,suite):
    base=(ROOT/package['path']).parent
    allnodes=list(nodes(suite['tree']))
    dirs=[d for _,data in allnodes for d in data['source_dirs']]
    entries=[];modules=[]
    for conditions,data in allnodes:
        entry=data.get('entry')
        if entry is not None:
            paths=resolve_paths(base,dirs,entry['value'],entry['kind']=='test-module')
            entries.append(dict(declaration_conditions=list(conditions),**entry,potential_paths=paths))
        for module in data['other_modules']:
            modules.append(dict(module=module,declaration_conditions=list(conditions),potential_paths=resolve_paths(base,dirs,module,True),autogenerated=module in data['autogen_modules']))
    found=any(p['availability']=='available' for e in entries for p in e['potential_paths'])
    return dict(id='dependency/cabal/'+package['path']+'/'+suite['name'],name=suite['name'],package=package['package'],source=source(package['path']),
                kind='declared-test-suite-root',conditional_tree=suite['tree'],entries=entries,declared_other_modules=modules,
                source_availability='entry-source-available' if found else 'entry-source-missing-or-unsupported',
                execution_units=dict(declared=1,source_entry_available=found,listed=None,runtime=None,assertions=None),
                condition_semantics='No branch selected. Potential paths use union of source directories across all branches, NOT a valid configuration or proof of buildability. Resolve all effective profiles before execution.',
                expectation_origin='Original source entry/module bodies and their unchanged fixtures; expected assertions have not been extracted or rewritten.',
                execution_command='cabal test '+package['package']+':test:'+suite['name']+' (upstream reproduction only; build/profile/toolchain unresolved)',
                status='unmapped',kotlin=None,gap='Runtime registration/leaf and independent expectation extraction, effective flags/OS/toolchains/build-tool generators, transitive source modules and Kotlin assertion mapping pending.')


def source_signals(path):
    text=(ROOT/path).read_text(errors='replace')
    signals=[]
    if Path(path).suffix in ('.hs','.lhs','.hsc'):
        tokens=census.lex_haskell(text) if re.search(r'^main\s*(?:::|=)',text,re.M) else []
        for index,t in enumerate(tokens[:-1]):
            if t['kind']=='token' and t['value']=='main' and tokens[index+1]['value'] in ('::','=') and (t['start']==0 or text[t['start']-1]=='\n'):
                signals.append(dict(kind='top-level-main-declaration',line=text.count('\n',0,t['start'])+1,offset=t['start']))
        for m in re.finditer(r'^\s*(?:--|\{-|\*)?\s*>>>[^\n]*',text,re.M):
            signals.append(dict(kind='embedded-doctest-prompt-candidate',line=text.count('\n',0,m.start())+1,offset=m.start(),original_line=m[0]))
        for m in re.finditer(r'\{-#\s*OPTIONS_GHC\b[^}]*\b(?:hspec-discover|tasty-discover)\b[^}]*#-\}',text):
            signals.append(dict(kind='preprocessor-factory',line=text.count('\n',0,m.start())+1,offset=m.start(),original_pragma=m[0]))
    return signals



def non_cabal_roots():
    """Source-inspected additional routes; not summed with aliased Cabal suites."""
    rows=[]
    definitions=[
      ('vendor/blaze-builder-0.4.4.1/Makefile','test','make-target-test-route','test:\n\t$(GHC) --make -fforce-recomp -O2 -itests -main-is Tests Tests',
       'vendor/blaze-builder-0.4.4.1/tests/Tests.hs','Tests.main -> defaultMain -> testGroup Tests tests',
       'make test; same Tests.hs entry as Cabal test, alias route rather than additional source tests.'),
      ('vendor/blaze-builder-0.4.4.1/Makefile','test-llvm-segfault','make-target-regression-route','test-llvm-segfault:\n\tghc-7.0.0.20100924 --make -fllvm',
       'vendor/blaze-builder-0.4.4.1/tests/LlvmSegfault.hs','LlvmSegfault.main prints the builder result; no explicit assertion/golden in this entry.',
       'make test-llvm-segfault requires historical ghc-7.0.0.20100924 and LLVM; not an executable assertion count.'),
      ('vendor/text-2.1.2/tests/literal-rule-test.sh','literal-rule-test','shell-compiler-regression-route','function check_firings()',
       'vendor/text-2.1.2/tests/LiteralRuleTest.hs','check_firings compiles LiteralRuleTest.hs, counts exact named GHC rule firings, compares original literal integers.',
       'bash literal-rule-test.sh from tests; compiler optimizer/version and shell/grep/wc semantics required.'),
      ('vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs','testSuite','argument-dispatched-test-route','("test":_) -> testSuite',
       'vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs','main -> myMain; first argument test selects testSuite; empty args require program-name imagetest, otherwise benchmark.',
       'Compiled original main test from original repository root with original fixture paths; fixture-release v3.3.8 differs from retained JuicyPixels library3.3.9.')]
    for path,name,kind,anchor,entry,registration,conditions in definitions:
        text=(ROOT/path).read_text()
        if text.count(anchor)!=1:raise ValueError('Reviewed non-Cabal registration drift: '+path+' '+name)
        rows.append(dict(id='dependency/non-cabal-root/'+path+'/'+name,name=name,kind=kind,source=source(path),registration_line=text.count('\n',0,text.index(anchor))+1,
                         entry_source=source(entry) if (ROOT/entry).is_file() else dict(path=entry,sha256=None,availability='missing-source'),
                         registration_semantics=registration,execution_conditions=conditions,expectation_origin='Original entry body and fixture/source expectations; no expected output generated.',
                         execution_units=dict(declared_route=1,listed=None,runtime=None,assertions=None),status='unmapped',kotlin=None,
                         gap='Route established only. Complete registration/assertion/fixture closure and reviewed executable Kotlin mappings remain pending.'))
    shell=(ROOT/'vendor/text-2.1.2/tests/literal-rule-test.sh').read_text()
    rows[2]['literal_assertion_sites']=[dict(id=rows[2]['id']+'/'+str(i),rule=m[1],expected_firings=int(m[2]),line=shell.count('\n',0,m.start())+1,
                                          status='unmapped',kotlin=None) for i,m in enumerate(re.finditer(r'^check_firings "([^"]+)" (\d+)\s*$',shell,re.M),1)]
    return rows

# Bounded original assertion bridge, separate from Cabal root registration units.
PSS_MAPPING='docs/test-inventory-dependency-pss.json'
PSS_SUITE='dependency/cabal/vendor/crypton-2.0.1/crypton.cabal/test-crypton'
PSS_COMMIT='5e968eea11f0c58013415acafe060f43dada9b62'
# Code-review authority: independently derived from accepted5e968eea Git blobs,
# actual immutable PSS core JAR bd788848..., and actual frozen JUnit XML. Data
# self-hashes cannot authorize replacement sources/classes/results/commands.
# These are provenance acceptance pins, not signatures or execution attestation.
PSS_PROOF_PINS={
    "sources": "c7d841e0feb0e3f4378be7acb382a5fdbf05ac307faefe9c5cba6f1c9cd244ee",
    "core_classes": "373c31be41b3862f0b01b9ca97fd5a6c3b8c83974ed972f327a0a73288e22336",
    "report": "d3b928610754c6e228355ed5030067f026a8868430da3c18c20494ed0da57df2",
    "proof": "6a394a57995b09643018390b47d8c8a38eda681a780168e555340063d59b1e36"
}


def pss_digest(value):
    return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(',',':')).encode()).hexdigest()


def pss_registration_order():
    # Exact expansion of the pinned spec, not corpus/XML storage order.
    rows=[]
    def add(components,label,helper,vector,index,key):rows.append(dict(components=['RSA-PSS']+components,source_case=label,expansion=dict(helper=helper,vector=vector,index=index,key=key)))
    for label,name in [('itself','the signature itself verifies'),('plusN','the same signature plus n is refused'),('equalN','a signature representative equal to the modulus is refused')]:
        add(['signature range',name],'range/'+label,'signatureRangeTests','first valid vectorsKey1',None,'rsaKey1')
    add(['signature internal','0'],'internal/sign/0','doSignTest','vectorInt',0,'rsaKeyInt')
    add(['verify internal','0'],'internal/verify/0','doVerifyTest','vectorInt',0,'rsaKeyInt')
    for width,key,vector in [('1024','rsaKey1','vectorsKey1'),('1025','rsaKey2','vectorsKey2'),('1026','rsaKey3','vectorsKey3'),('1031','rsaKey8','vectorsKey8')]:
        for group,operation,helper in [('signature','sign','doSignTest'),('verify','verify','doVerifyTest')]:
            for index in range(6):add([group+' key '+width,str(index)],width+'/'+operation+'/'+str(index),helper,vector,index,key)
    return rows


def validate_pss_mapping(data,suites):
    if data['schema_version']!=1 or data['kind']!='accepted_dependency_module_assertion_mapping' or data['suite_ref']!=PSS_SUITE or data['module']!='PubKey.PSSSpec':raise ValueError('PSS module/root mapping identity differs')
    root=[r for r in suites if r['id']==PSS_SUITE]
    if len(root)!=1 or root[0]['status']!='unmapped' or root[0]['kotlin'] is not None:raise ValueError('PSS proof cannot promote entire crypton suite')
    if not any(m['module']=='PubKey.PSSSpec' for m in root[0]['declared_other_modules']):raise ValueError('PSS module absent from declared crypton root')
    if data['scope']!='Exactly53original PubKey.PSSSpec assertions mapped; crypton suite/root remains unmapped and full622/613/Pandoc/calibre open':raise ValueError('PSS scope cannot claim full dependency coverage')
    units=dict(original_source_assertions=53,matched_kotlin_executions=53,new_pandoc_tasty_leaves=0,whole_crypton_suite_complete=False)
    if pss_digest(data['counting_units'])!=pss_digest(units):raise ValueError('PSS source/execution/root counting differs')
    proof=data['proof'];core=proof['core_artifact'];report=proof['junit']
    if proof['accepted_commit']!=PSS_COMMIT or core['sha256']!='bd78884871b505c46617fe6021122134bfc526d1c0fe356d683cff34c8e1cf2a':raise ValueError('PSS immutable accepted artifact identity differs')
    # New-only RSA.Types files are not inferred dependencies of the old artifact.
    if any('CryptoRsaTypes' in path for path in proof['source_bindings']):raise ValueError('Unaccepted RSA.Types source in PSS proof')
    for path,digest in proof['source_bindings'].items():
        if source(path)['sha256']!=digest:raise ValueError('PSS accepted/current source hash drift: '+path)
    corpus=proof['corpus'];raw=(ROOT/corpus['path']).read_bytes();plain=gzip.decompress(raw)
    if hashlib.sha256(raw).hexdigest()!=corpus['gzip_sha256'] or hashlib.sha256(plain).hexdigest()!=corpus['json_sha256']:raise ValueError('PSS source corpus hash drift')
    source_rows=json.loads(plain)
    if len(source_rows)!=53 or corpus['source_case_count']!=53 or len({r['sourceCase'] for r in source_rows})!=53:raise ValueError('PSS source corpus labels/count differ')
    indexed={row['sourceCase']:(index,row) for index,row in enumerate(source_rows)}
    full=ET.fromstring(report['original_suite_start_tag']+'</testsuite>')
    if full.get('name')!='io.heapy.kotdoc.crypto.CryptoRsaPssTest' or full.get('tests')!='10136' or any(full.get(k)!='0' for k in ('failures','errors','skipped')):raise ValueError('PSS historical whole-report result differs')
    if report['full_result']!=dict(tests=10136,failures=0,errors=0,skips=0) or report['selected_result']!=dict(tests=53,failures=0,errors=0,skips=0):raise ValueError('PSS53 subset is not whole10136 result')
    cases={}
    for raw_case in report['raw_cases']:
        case=ET.fromstring(raw_case.strip());name=case.get('name')
        if case.tag!='testcase' or case.get('classname')!='io.heapy.kotdoc.crypto.CryptoRsaPssTest' or name in cases or any(case.find(tag) is not None for tag in ('failure','error','skipped')):raise ValueError('PSS duplicate/class/result XML case differs')
        cases[name]=raw_case
    if set(cases)!={f'SourceCases/{i}' for i in range(53)}:raise ValueError('PSS exact53 XML execution identities differ')
    registrations=data['registrations'];expected=pss_registration_order()
    if len(registrations)!=53:raise ValueError('PSS original leaf count differs')
    seen_ids=set();seen_corpus=set()
    for ordinal,(row,original) in enumerate(zip(registrations,expected),1):
        identity=PSS_SUITE+'/module/PubKey.PSSSpec/spec/'+json.dumps(original['components'],separators=(',',':'))+'#1'
        if row['id']!=identity or row['components']!=original['components'] or row['occurrence']!=1 or row['original_source_ordinal']!=ordinal or pss_digest(row['expansion'])!=pss_digest(original['expansion']) or row['source_case']!=original['source_case']:raise ValueError('PSS original registration/order/expansion differs')
        if row['suite_ref']!=PSS_SUITE or row['module']!='PubKey.PSSSpec' or row['source']!='vendor/crypton-2.0.1/tests/PubKey/PSSSpec.hs' or row['status']!='mapped':raise ValueError('PSS leaf scope differs')
        index,observed=indexed[original['source_case']];name=f'SourceCases/{index}';k=row['kotlin']
        if any(type(row[field]) is not int for field in ('occurrence','original_source_ordinal','corpus_index')) or type(k['dynamic_ordinal']) is not int:raise ValueError('PSS ordinal must be an integer')
        if row['corpus_index']!=index or row['corpus_row_sha256']!=pss_digest(observed) or row['expected_sha256']!=pss_digest(observed['expected']):raise ValueError('PSS original expectation/corpus join differs')
        if k!=dict(source='core/src/test/kotlin/io/heapy/kotdoc/crypto/CryptoRsaPssTest.kt',class_name='io.heapy.kotdoc.crypto.CryptoRsaPssTest',factory='originalSource53',display_name=name,dynamic_ordinal=index+1,observer='PssReplay.observe') or row['execution_case_xml']!=cases[name]:raise ValueError('PSS Kotlin factory/ordinal/XML join differs')
        seen_ids.add(identity);seen_corpus.add(index)
    if len(seen_ids)!=53 or seen_corpus!=set(range(53)):raise ValueError('PSS original/execution join is not bijective')
    # Independent accepted artifact/result/source pins, not re-sealable JSON maps.
    actual=dict(sources=pss_digest(proof['source_bindings']),core_classes=pss_digest(core['production_classes']),report=pss_digest(report),proof=pss_digest(proof))
    if actual!=PSS_PROOF_PINS:raise ValueError('PSS independent accepted proof identity differs')
    return data


def inventory():
    captured=validate_capture(json.loads(CAPTURE.read_text()))
    allfiles=sorted(p for p in (ROOT/'vendor').rglob('*') if p.is_file())
    catalog=[source(p.relative_to(ROOT)) for p in allfiles]
    suites=[suite_record(package,suite) for package in captured['packages'] for suite in package['test_suites']]
    pss=validate_pss_mapping(json.loads((ROOT/PSS_MAPPING).read_text()),suites)
    entry_refs={}
    for s in suites:
        for e in s['entries']:
            for p in e['potential_paths']:
                if p['availability']=='available':entry_refs.setdefault(p['path'],[]).append(s['id'])
    executable_paths=set()
    for package in captured['packages']:
        base=(ROOT/package['path']).parent
        for exe in package['executables']:
            ns=list(nodes(exe['tree']));dirs=[d for _,data in ns for d in data['source_dirs']]
            for _,data in ns:
                if data['main_is']:
                    executable_paths.update(p['path'] for p in resolve_paths(base,dirs,data['main_is']) if p['availability']=='available')
    signals=[];runners=[]
    for p in allfiles:
        path=str(p.relative_to(ROOT))
        if p.suffix in ('.hs','.lhs','.hsc'):
            found=source_signals(path)
            if found:
                role='cabal-test-entry' if path in entry_refs else 'package-setup' if p.name in ('Setup.hs','Setup.lhs') else 'declared-non-test-executable' if path in executable_paths else 'unreconciled-source-candidate'
                signals.append(dict(id='dependency/source-signals/'+path,source=source(path),role=role,suite_refs=sorted(entry_refs.get(path,[])),signals=found,status='unmapped',kotlin=None,
                                    gap='Lexical signals only: prompts are not grouped assertion expectations, main declarations may be non-tests; factory expansion/registration reachability pending.'))
        if p.suffix in ('.sh','.py','.tcl') or p.name.lower() in ('makefile','gnumakefile','shakefile.hs'):
            runners.append(dict(id='dependency/non-cabal-runner-candidate/'+path,source=source(path),kind='retained-script-or-build-file',status='unmapped',kotlin=None,
                                gap='File availability only; targets/factory semantics and test reachability not yet audited. No test classification/count inferred from filename.'))
    roots=[]
    for directory in sorted(p for p in (ROOT/'vendor').iterdir() if p.is_dir()):
        prefix=str(directory.relative_to(ROOT))+'/'
        packages=[p for p in captured['packages'] if p['path'].startswith(prefix)]
        files=[s for s in catalog if s['path'].startswith(prefix)]
        roots.append(dict(id='dependency/root/'+directory.name,path=prefix[:-1],retained_files=len(files),file_catalog_sha256=hashlib.sha256(json.dumps(files,sort_keys=True,separators=(',',':')).encode()).hexdigest(),
                          package_manifests=[p['path'] for p in packages],declared_suite_refs=[s['id'] for s in suites if s['source']['path'].startswith(prefix)],
                          source_extent='Retained checkout only; complete upstream release/test-tree availability is not inferred from directory or manifest presence.',
                          gap='No retained Cabal declaration; upstream declared suites unavailable/unknown. Embedded/non-Cabal candidates retained separately.' if not packages else 'Every retained Cabal test stanza reconciled; embedded/non-Cabal registration completeness and missing release files remain separate.'))
    frozen=[]
    for name,version in re.findall(r'\bany\.([A-Za-z0-9_-]+)\s*==\s*([0-9.]+)',(ROOT/FREEZE).read_text()):
        package=name+'-'+version
        available=[p['path'] for p in captured['packages'] if p['package']==package]
        frozen.append(dict(id='dependency/frozen/'+package,package=package,matching_retained_manifests=available,
                           availability='retained-manifest' if available else 'no-exact-retained-manifest',
                           gap='Installed/compiler packages and source fragments do not establish original suite availability; obtain exact original manifest/source when absent.'))
    if len({r['id'] for r in frozen})!=len(frozen):raise ValueError('Duplicate frozen dependency version')
    return dict(schema_version=1,capture=source(str(CAPTURE.relative_to(ROOT))),observer=source(OBSERVER),freeze=source(FREEZE),
                scope='Complete retained-vendor Cabal root declarations, not complete upstream dependency suites/leaves. Frozen dependency and every retained vendor directory reconciled explicitly.',
                counting_units=dict(declared='Cabal component roots',available='retained source entry candidates; no buildability inference',listed='Whole-component registration listing unknown; selected PSS module source assertions separately mapped',runtime='Whole-root runtime multiplicity unknown; selected PSS53 executions separately retained',assertion='Root assertion totals unknown; separately accepted PSS53 module assertion mappings below'),
                catalog=catalog,roots=roots,frozen_dependencies=frozen,cabal_test_suites=suites,
                accepted_module_assertion_mappings=[dict(evidence_source=source(PSS_MAPPING),**pss)],
                cabal_non_test_executables=[dict(source=source(p['path']),package=p['package'],executables=p['executables']) for p in captured['packages'] if p['executables']],
                embedded_and_main_signals=signals,non_cabal_runner_candidates=runners,reviewed_non_cabal_routes=non_cabal_roots(),
                gap='Embedded prompt grouping/execution semantics, standalone/build targets, generated factories, source/import registration closure, unavailable exact releases, platform/toolchain profiles, runtime leaves and remaining Kotlin mappings remain required; selected PSS53 module assertions do not promote the whole crypton root. Calibre directional reconciliation is a separate next checkpoint.')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    action=parser.add_mutually_exclusive_group(required=True)
    action.add_argument('--capture',type=Path,metavar='PARSER_BINARY');action.add_argument('--verify-capture',type=Path,metavar='PARSER_BINARY');action.add_argument('--summary',action='store_true')
    args=parser.parse_args()
    if args.capture:capture(args.capture,True)
    elif args.verify_capture:
        if capture(args.verify_capture)!=json.loads(CAPTURE.read_text()):raise ValueError('Cabal AST replay differs')
        print('Verified original Cabal AST; no tests run')
    else:
        data=inventory()
        print(len(data['roots']),'retained roots;',len(data['cabal_test_suites']),'declared suite roots;',len(data['frozen_dependencies']),'frozen dependencies;',len(data['embedded_and_main_signals']),'source signal files; 53 selected PSS assertions mapped, whole-component coverage remains unknown')
