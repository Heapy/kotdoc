#!/usr/bin/env python3
"""Conservative source-to-implementation/proof classification, never name=coverage."""
from pathlib import Path
import collections,hashlib,json,re
from census import census,ROOT
HERE=Path(__file__).resolve().parent
AST=ROOT/'core/src/main/kotlin/io/heapy/kotdoc/ast'
WALK='9f9c6cb2962cfb4d28503bfb5588507df0478aad'
READ='06317ca31379e7fb09a1c4579d4167e50f55d62a'
ORDER='efeebad8f6d307e9245978f9ed631af431ea1b99'
PROPERTIES='66722c107b7663ded9c8f510a41e05c3e131a970'
def reference(file,name):
    p=AST/file;text=p.read_text();patterns=[r'\b(?:fun\s+(?:<[^\n]*?>\s*)?|val\s+|class\s+|interface\s+)'+re.escape(name)+r'\b',r'\b'+re.escape(name)+r'\b']
    match=next((m for pat in patterns if (m:=re.search(pat,text))),None)
    return dict(path=str(p.relative_to(ROOT)),line=text[:match.start()].count('\n')+1 if match else None,symbol=name)
def classify(row):
    module,name=row['id'].split('/',1)
    if module=='Walk':
        symbol='Walkable' if name=='Walkable' else name.replace("M'",'MPrime').replace("'",'Prime')
        row.update(kotlin=[reference('PandocWalk.kt',symbol)],status='accepted_finite_source_matrix',
                   finite_proof=[dict(commit=WALK,manifest='tools/haskell-types-walk-reference/source-manifest.json',scope='58 source dictionaries/174 methods and24 helpers;5715 represented observations plus2 source-only StrictData failures')],
                   original_tests=['test/test-pandoc-types.hs:12 Walk predicates','tools/haskell-types-walk-reference/TypesWalkReference.hs:actual original module'],remaining_gap='Accepted174-slot matrix plus independently accepted pure-demand/DefinitionList correction:47 original Walk observations and5 adapter checks,11777 actual regression cases. Explicit thunk/lazy-pair adapters represent concrete partial fields; no general GHC heap/infinite-spine claim. tools/haskell-types-walk-pure-reference retains52 native rows including5 source-only Generic contrasts.')
    elif module=='Generic':
        row.update(kotlin=[reference('PandocGeneric.kt',name)],status='accepted_finite_source_matrix',original_tests=['Generic.hs:Haddock topDown/bottomUp normal example','tools/haskell-types-generic-reference:807 original requests; all77Definition constructors, aliases, primitive/container targets, customData/monad demand and deep cases'],finite_proof=[dict(commit='1171e93b2c7666043e6bbfac387fd81f76f1b22c',manifest='tools/haskell-types-generic-reference/source-manifest.json',scope='Independent807original/interface/schema/protocol replay and12585zero regression accepted; exactsybsource/license/package notices accepted')],remaining_gap='Four-function checkpoint independently accepted. Explicit source thunk/Data/Monad representations execute finite partial-field cases; strict existing Kotlin scalar records retain their documented boundary. This does not certify all Data/Typeable/GHC.Generic/NFData instance members or close the140/350/JSON audit.')
    elif module=='Definition':
        numeric=name in {'RowHeadColumns','RowSpan','ColSpan'}
        aliases={'Attr','Target','ListAttributes','ColSpec','ShortCaption'}
        functions={'nullMeta','isNullMeta','lookupMeta','docTitle','docAuthors','docDate','nullAttr','pandocTypesVersion','SimpleFigure'}
        if name in functions:
            if name in {'nullMeta','isNullMeta','lookupMeta','docTitle','docAuthors','docDate'}:
                row.update(kotlin=[reference('PandocMetadata.kt',name)],status='implemented_metadata_awaiting_review',finite_proof=[dict(manifest='tools/haskell-types-metadata-reference/source-manifest.json',scope='Source helper branches,unicode/recursive/deep/demand;5145authorJUnitcases; legacytitle/date/authors delegation regressions')],remaining_gap='Independent native finite/demand and isolated proof pass; shared Gradle/review pending. Explicit source-list/root adapters, not arbitrary GHC heaps or JVM Collection consumer equivalence.')
            elif name=='SimpleFigure':
                row.update(kotlin=[reference('Metadata.kt',name)],status='implementation_present_proof_partial',remaining_gap='Direct source body matches the intended helper/pattern; complete independent case/demand/Unicode proof and member surface audit still required.')
            elif name=='pandocTypesVersion':
                row.update(kotlin=[reference('PandocJson.kt','apiVersion')],status='representation_partial',remaining_gap='API-version branch exists; source returns Data.Version.Version, not only a numeric list. Public value/record/dependency behavior not yet mapped.')
            else:
                row.update(kotlin=[reference('Pandoc.kt','Attr' if name=='nullAttr' else 'Pandoc')],status='api_mapping_unverified',remaining_gap='Equivalent empty Attr/Map, isEmpty/get operations exist, but named public source contract, independent examples and partial-value behavior are not yet verified.')
        elif numeric:
            row.update(kotlin=[reference('PandocCounts.kt',name),reference('PandocCounts.kt','CountDictionary'),reference('CountBridge.kt','CountBridge')],status='accepted_num_enum_dictionary_matrix',finite_proof=[dict(manifest='tools/haskell-types-count-reference/source-manifest.json',scope='3distinctcounttypes/45NumEnumslots;9930originalfinite+48demandresponses and351legacyASTbridgecases')],remaining_gap='Num/Enum source defaults, Int64 overflow, BigInteger conversion, lazy ranges and explicit newtype thunk adapters implemented; independently accepted atdb0341b4533491b61eb9998a6e5fb26919a5d7c9. Existing AST Long fields preserved through explicit bridge. Other derived count class members remain separate gates.')
        else:
            row.update(kotlin=[reference('PandocRead.kt',name)] if name=='Meta' or name in aliases else [reference('Format.kt' if name=='Format' else 'Pandoc.kt',name)],status='representation_and_selected_instances',remaining_gap='Concrete representation and selected JSON/Read/Show/EqOrd evidence exist; expand all constructor/record/typeclass members. Data/Typeable/GHC.Generic/NFData surface is not certified by those slices.')
            row['finite_proof']=[dict(commit=READ,scope='25 concrete Read dictionaries plus5 aliases;16954 original requests'),dict(commit=ORDER,scope='25 EqOrd dictionaries ×9 methods; accepted finite matrix')]
            if name in {'Meta','Pandoc'}:row['finite_proof'].append(dict(manifest='tools/haskell-types-metadata-reference/source-manifest.json',scope='Meta/Pandoc Semigroup3/Monoid3 each;25metadata methodslots+10helpers author proof pending review'))
            row['original_tests']=['test/test-pandoc-types.hs:56 paired JSON literals and roundtrip property','tools/haskell-types-read-reference/TypesReadReference.hs','tools/haskell-types-order-closure-reference/TypesOrderClosureReference.hs']
    elif module=='Builder':
        tables={'normalizeTableHead':'normalizeHead','normalizeTableBody':'normalizeBody','normalizeTableFoot':'normalizeFoot','placeRowSection':'placeRowSection','clipRows':'clipRows'}
        if name in tables:
            row.update(kotlin=[reference('Tables.kt',tables[name])],status='implementation_present_proof_partial',finite_proof=[dict(commit=PROPERTIES,scope='Nine original table properties/two original literals;4500 original table inputs')],original_tests=['test/test-pandoc-types.hs:table properties/literals'],remaining_gap='Original property matrix accepted; independently audit each public function bounds, remainder/demand contract and out-of-generator-domain inputs. Iterator Placement API is not by itself proof of original lazy triple result.')
        elif name=='Many':
            row.update(kotlin=[reference('PandocMany.kt','Many'),reference('PandocMany.kt','unMany')],status='accepted_finite_dictionary_matrix',finite_proof=[dict(manifest='tools/haskell-types-many-reference/source-manifest.json',scope='42slots:Functor2/Foldable17/Traversable4/specializedalgebra13/helpers+representation6; source-classdefaults/callbackdemand/Seqshape/Int64/DoubleandBigIntegerproofs')],remaining_gap='Generic Many carrier and42-slot slice implemented with independent originals, final17713Gradle/package passed, accepted at5adcfdb5972f62bb81bdfbc4531610b790e0f94e. Data/Eq/Ord/Typeable/Show/Read/Generic members for arbitrary element dictionaries remain separate open gates; concrete Inlines/Blocks evidence is not silently generalized.')
        elif name in {'singleton','toList','fromList','isNull'}:
            row.update(kotlin=[reference('PandocMany.kt','toSourceList'if name=='toList'else name)],status='accepted_finite_dictionary_matrix',finite_proof=[dict(manifest='tools/haskell-types-many-reference/source-manifest.json',scope='Many42-slot source matrix; generic helpers/constructor/selector and independent source-list step/head/tail through2^40elements')],remaining_gap='Independent finite parity and17713Gradle/package accepted at5adcfdb5972f62bb81bdfbc4531610b790e0f94e. Source-list adapters and finite JVM List convenience have explicit different size/demand bounds; no arbitrary GHC heap equivalence claim.')
        elif name in {'ToMetaValue','HasMeta'}:
            row.update(kotlin=[reference('MetadataConversions.kt','ToMetaValue'),reference('PandocMetadata.kt','HasMeta')],status='implemented_metadata_awaiting_review',finite_proof=[dict(manifest='tools/haskell-types-metadata-reference/source-manifest.json',scope='All9ToMetaValue and2HasMeta dictionaries/13methods, recursive typed native recipes andcustomdictionary demand; metadata25methodslots/10helpers')],remaining_gap='Author full finite/demand/interface proof passes; shared Gradle and independent review pending. Source Text/String and Map Text/Map String remain distinct explicit dictionaries; source compiler typeclass selection is caller-explicit.')
        elif name in {'doc','setTitle','setAuthors','setDate'}:
            row.update(kotlin=[reference('PandocMetadata.kt',name)],status='implemented_metadata_awaiting_review',finite_proof=[dict(manifest='tools/haskell-types-metadata-reference/source-manifest.json',scope='Original source function/direct finite and unusedargument demand proof')],remaining_gap='Author finite/demand proof passes; shared Gradle and independent review pending. Remaining unrelated Builder exports and generic source classes stay open.')
        elif name in {'Inlines','Blocks'}:
            row.update(kotlin=[reference('Builder.kt',name)],status='representation_and_selected_instances',finite_proof=[dict(commit=READ,scope='Concrete Many Read'),dict(commit=ORDER,scope='Concrete Many EqOrd'),dict(commit=WALK,scope='Many traversable finite contexts')],remaining_gap='Many42-slot specialized algebra/Functor/Foldable/Traversable/IsString author slice is implemented with originals, final17713Gradle/package accepted at5adcfdb5972f62bb81bdfbc4531610b790e0f94e. Existing List-backed aliases remain compatibility representations; generic Many class members/dependency contracts are not all certified.')
        else:
            row.update(kotlin=[reference('Builder.kt',name)],status='implementation_present_proof_partial',original_tests=['core/src/test/kotlin/io/heapy/kotdoc/ast/BuilderTest.kt:source-derived examples (not blanket original-native proof)'],remaining_gap='Source implementation found; exact input/output/whitespace/alias/constructor behavior needs per-export independent native mapping, not merely the matching name.')
            if name in {'figure','figureWith','simpleFigure','simpleFigureWith'}:row['finite_proof']=[dict(commit=PROPERTIES,scope='Original Figure property and305 Figure examples; does not certify every Builder argument combination')]
    return row

def instances():
    rows=[];derivations=[]
    for module in ['Definition','Builder','Walk','Arbitrary','JSON']:
        p=ROOT/f'upstream/pandoc-types/src/Text/Pandoc/{module}.hs';source=p.read_text()
        for m in re.finditer(r'(?ms)^instance\b.*?(?=^instance\b|^--|^\w|\Z)',source):
            clause=m.group();rows.append(dict(module=module,source=str(p.relative_to(ROOT)),line=source[:m.start()].count('\n')+1,source_clause=clause,status='accepted_finite_matrix'if module in {'Walk','Arbitrary'}else'instance_members_audit_pending'))
        declarations=list(re.finditer(r'^(?:data|newtype)\s+(\w+)',source,re.M))
        for m in re.finditer(r'\bderiving\s*(?:\(([^)]*)\)|instance\s+([^\n]+))',source):
            previous=[d for d in declarations if d.start()<m.start()]
            derivations.append(dict(module=module,line=source[:m.start()].count('\n')+1,type_context=previous[-1].group(1)if previous else None,source=m.group(),status='expand_and_map_members'))
    return dict(explicit_instance_clauses=rows,deriving_clauses=derivations,template_json_note='Definition derives ToJSON/FromJSON for17 named types via Template Haskell; expand separately with manual/derived instances. Do not infer these from explicit-instance regex.')
def expanded_instances(raw):
    rows=[]
    for item in raw['explicit_instance_clauses']:
        header=item['source_clause'].split('where',1)[0]
        header=re.sub(r'\{-#.*?#-\}|\bOVERLAPS\b','',header,flags=re.S)
        header=' '.join(header.removeprefix('instance').split())
        tail=header.rsplit('=>',1)[-1].strip()
        match=re.match(r'(\w+)\s+(.*)',tail)
        assert match,header
        rows.append(dict(module=item['module'],class_name=match.group(1),instance_type=match.group(2),origin='explicit',source_line=item['line'],source_header=header,status=item['status']))
    for item in raw['deriving_clauses']:
        clause=item['source']
        if clause.startswith('deriving instance'):
            tail=clause[len('deriving instance'):].strip();name,ty=tail.split(' ',1)
            rows.append(dict(module=item['module'],class_name=name,instance_type=ty,origin='standalone_deriving',source_line=item['line'],status='member_audit_pending'))
        else:
            for name in clause[clause.index('(')+1:clause.rindex(')')].split(','):
                rows.append(dict(module=item['module'],class_name=name.strip(),instance_type=item['type_context'],origin='deriving',source_line=item['line'],status='member_audit_pending'))
    source=(ROOT/'upstream/pandoc-types/src/Text/Pandoc/Definition.hs').read_text()
    th=source[source.index('in concat <$> traverse'):source.index('-- Instances for deepseq')]
    names=re.findall(r"''(\w+)",th);assert len(names)==17
    for name in names:
        for cls in ['ToJSON','FromJSON']:rows.append(dict(module='Definition',class_name=cls,instance_type=name,origin='TemplateHaskell_deriveJSON',status='member_audit_pending'))
    assert len(rows)==350,len(rows)
    return dict(source_instance_count=len(rows),module_counts=dict(collections.Counter(r['module']for r in rows)),class_counts=dict(collections.Counter(r['class_name']for r in rows)),instances=rows,note='Source-owned instance declarations/templates only, not all dependency instances. Each class still requires exact member/default and represented-domain audit. Generic Many is one source template, not silently replaced by two concrete wrappers.')

def main():
    data=census();data['exports']=[classify(r)for r in data['exports']]
    data['status_counts']=dict(collections.Counter(r['status']for r in data['exports']))
    data['package_supplement']=[dict(module='JSON',export='ToJSONFilter(..)',source='upstream/pandoc-types/src/Text/Pandoc/JSON.hs',status='missing_implementation',gap='One additional package-owned export/class, six source ToJSONFilter instances: pure, list expansion, monadic, monadic expansion, all args, Maybe Format. No Kotlin equivalent found; full611 closure must resolve it.'),dict(module='Arbitrary',exports=[],status='accepted_finite_instance_matrix',proof='19 orphan Arbitrary instances plus exact generators/shrinkers in accepted611 checkpoints; exported helpers are none, since module header explicitly exports().')]
    (HERE/'export-matrix.json').write_text(json.dumps(data,indent=2)+'\n')
    inst=instances();(HERE/'expanded-instances.json').write_text(json.dumps(expanded_instances(inst),indent=2)+'\n');(HERE/'instance-census.json').write_text(json.dumps(inst,indent=2)+'\n')
    print(data['status_counts']);print('explicit instances',dict(collections.Counter(r['module']for r in inst['explicit_instance_clauses'])),'deriving clauses',len(inst['deriving_clauses']))
if __name__=='__main__':main()
