#!/usr/bin/env python3
"""Finite nine-leaf source/execution bridge regressions; no conversion execution."""
import copy
import base64
import io
import zipfile
from unittest.mock import patch
import hashlib
import importlib.util
import json
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('fb2_inventory',ROOT/'tools/fb2-test-inventory.py')
f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)


class FB2InventoryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data=f.inventory();cls.rows=cls.data['unit_leaves']
        cls.execution=json.loads((ROOT/f.EXECUTION).read_text())
        cls.evidence=json.loads((ROOT/cls.execution['evidence']['path']).read_text())
        cls.history=json.loads((ROOT/cls.execution['history']['path']).read_text())

    def test_exact_nine_runtime_unit_leaves_and_executed_assertions(self):
        self.assertEqual(len(self.rows),9);self.assertEqual(len({r['id'] for r in self.rows}),9)
        self.assertEqual([r['components'][-1] for r in self.rows[:6]],['Emphasis','Titles','Epigraph','Poem','Meta','Notes'])
        self.assertTrue(all(r['status']=='mapped' and r['kotlin']['execution_identity'] for r in self.rows))
        self.assertEqual(self.data['execution_result']['result'],dict(tests=9,failures=0,errors=0,skips=0))

    def test_reader_full_original_golden_and_actual_names(self):
        for row in self.rows[:6]:
            self.assertEqual(row['kotlin']['display_name'],row['components'][-1].lower())
            self.assertTrue(row['expectation_origin']['path'].endswith('.native'))
            self.assertIn('goldenVsString exact bytes',row['original_comparator'])
            self.assertIn('full NativeWriter.write',row['kotlin_comparator'])
            self.assertNotIn(b'\r',(ROOT/row['expectation_origin']['path']).read_bytes())

    def test_writer_para_alias_and_original_group_paths_explicit(self):
        para=self.rows[6]
        self.assertEqual(para['components'][-2:],['block elements','para'])
        self.assertEqual(para['alias'],dict(source_label='para',kotlin_label='paragraph',explicit=True))
        self.assertEqual(self.rows[7]['components'][-2:],['inlines','Emphasis'])
        self.assertEqual(self.rows[7]['kotlin']['display_name'],'emphasis')

    def test_haskell_unicode_and_original_literal_formatter(self):
        self.assertEqual(f.hs_string('"\\x2022 first"'),'• first')
        with self.assertRaisesRegex(ValueError,'Unsupported'):f.hs_string('"\\q"')
        self.assertIn('• first',self.rows[8]['source_registration']['expected_inner'])
        self.assertIn('terminal LF is ignored',self.rows[6]['original_comparator'])
        self.assertIn('plus one final LF',self.rows[6]['kotlin_comparator'])

    def test_wrong_kotlin_input_does_not_map_by_name_or_expected_only(self):
        with self.assertRaisesRegex(ValueError,'construction differs'):
            f.input_contract('para "Lorem ipsum cetera."','Builder.para(Builder.text("changed"))','para')

    def test_other_ten_obligations_do_not_inherit_unit_coverage(self):
        other=self.data['other_pending']
        self.assertEqual(len(other),10)
        self.assertEqual(sum(r['kind']=='old-cli-golden' for r in other),8)
        self.assertEqual(sum(r['kind']=='command-golden' for r in other),1)
        self.assertEqual(sum(r['kind']=='image-size-fixture' for r in other),1)
        self.assertTrue(all(r['status']=='unmapped' and r['kotlin'] is None and r['expectation_origin'] for r in other))
        self.assertTrue(all('runtime' in r or r['kind']=='image-size-fixture' for r in other))

    def test_missing_or_duplicate_runtime_root_rejected(self):
        saved=json.loads((ROOT/'docs/test-inventory-runtime.json').read_text())
        target=self.rows[0]['components'];index=saved['paths'].index(target)
        for duplicate in (False,True):
            changed=copy.deepcopy(saved)
            if duplicate:
                changed['paths'].append(saved['paths'][index]);changed['names'].append(saved['names'][index])
            else:changed['paths'].pop(index);changed['names'].pop(index)
            with self.assertRaisesRegex(ValueError,'missing/duplicate'):f.select_runtime(changed,target)

    def test_pending_native_refresh_cannot_promote_old_execution(self):
        changed=copy.deepcopy(self.execution);changed['refresh_pending']=True
        with self.assertRaisesRegex(ValueError,'refresh pending'):f.validate_execution(changed,self.rows)

    def test_execution_hash_case_failure_and_skip_rejected(self):
        for mode in ('hash','case','failure','skip'):
            changed=copy.deepcopy(self.execution);report=changed['reports'][0]
            if mode=='hash':report['xml_without_hostname_sha256']='0'*64
            else:
                tree=ET.fromstring(report['xml_without_hostname'])
                if mode=='case':tree.find('testcase').set('name','invented')
                elif mode=='failure':ET.SubElement(tree.find('testcase'),'failure')
                else:tree.set('skipped','1')
                xml=ET.tostring(tree,encoding='unicode');report['xml_without_hostname']=xml;report['xml_without_hostname_sha256']=hashlib.sha256(xml.encode()).hexdigest()
            with self.assertRaises(ValueError):f.validate_execution(changed,self.rows)

    def test_execution_source_drift_or_missing_file_rejected(self):
        changed=copy.deepcopy(self.execution);changed['relevant_sources_before_and_after'].pop('core/build.gradle.kts')
        with self.assertRaisesRegex(ValueError,'scope incomplete'):f.validate_execution(changed,self.rows)
        changed=copy.deepcopy(self.execution);path=next(iter(changed['relevant_sources_before_and_after']));changed['relevant_sources_before_and_after'][path]='0'*64
        with self.assertRaisesRegex(ValueError,'source/hash drift'):f.validate_execution(changed,self.rows)

    def test_runtime_overlay_changes_exactly_these_nine_ids(self):
        native=dict(leaves=[dict(id=r['id'],components=r['components'],status='unmapped',kotlin=None) for r in self.rows]+[dict(id='unrelated',components=['pandoc tests','Other'],status='unmapped',kotlin=None)])
        f.apply_runtime_bridge(native,self.data)
        self.assertEqual(sum(r['status']=='mapped' for r in native['leaves']),9)
        self.assertEqual(native['leaves'][-1]['status'],'unmapped')
        native['leaves'].pop(0)
        with self.assertRaisesRegex(ValueError,'root omitted'):f.apply_runtime_bridge(native,self.data)

    def test_console_mode_is_explicit_and_raw_engine_names_are_not_gradle_names(self):
        self.assertEqual((self.execution['schema_version'],self.execution['execution_mode']),(2,'junit-console'))
        self.assertNotIn('actual_shared_command',self.execution)
        self.assertEqual(ET.fromstring(self.execution['reports'][0]['xml_without_hostname']).get('name'),'JUnit Jupiter')
        self.assertTrue(all('()[' in c.get('name') for c in ET.fromstring(self.execution['reports'][0]['xml_without_hostname']).findall('testcase')))

    def console_check(self,evidence=None,history=None,saved=None):
        return f._validate_console_evidence(saved or self.execution,self.rows,evidence or self.evidence,history or self.history)

    def test_console_missing_extra_factory_and_mutable_classpath_rejected(self):
        for mode in ('missing','extra','mutable','unrecorded'):
            evidence=copy.deepcopy(self.evidence);saved=copy.deepcopy(self.execution);run=evidence['run_command']
            if mode=='missing':
                index=run.index('--select-method');del run[index:index+2]
            elif mode=='extra':run+=['--select-class','unrelated.Test']
            else:
                index=run.index('--class-path')+1
                run[index]+=':'+('core/build/classes/kotlin/main' if mode=='mutable' else '/tmp/unrecorded.jar')
            saved['actual_command_args']=run
            with self.assertRaises(ValueError):self.console_check(evidence=evidence,saved=saved)

    def test_console_evidence_hash_or_unknown_mode_rejected(self):
        for mode in ('hash','mode'):
            saved=copy.deepcopy(self.execution)
            if mode=='hash':saved['evidence']['sha256']='0'*64
            else:saved['execution_mode']='pretend-gradle'
            with self.assertRaises(ValueError):f.validate_execution(saved,self.rows)

    def test_console_jars_and_bytecode_identity_rejected(self):
        for mode in ('core','test','class'):
            evidence=copy.deepcopy(self.evidence)
            if mode=='core':evidence['core_package']['sha256']='0'*64
            elif mode=='test':evidence['test_jar']['sha256']='0'*64
            else:evidence['core_package']['selected_classes'][next(iter(evidence['core_package']['selected_classes']))]='0'*64
            with self.assertRaises(ValueError):self.console_check(evidence=evidence)

    def test_console_test_jar_cannot_overlay_production_classes(self):
        evidence=copy.deepcopy(self.evidence);entries=f._archive(evidence['test_jar']);buffer=io.BytesIO()
        with zipfile.ZipFile(buffer,'w',zipfile.ZIP_DEFLATED) as archive:
            for name,raw in entries.items():archive.writestr(name,raw)
            archive.writestr('io/heapy/kotdoc/ast/Builder.class',b'unreviewed')
        raw=buffer.getvalue();evidence['test_jar']=dict(base64=base64.b64encode(raw).decode(),sha256=hashlib.sha256(raw).hexdigest())
        with self.assertRaisesRegex(ValueError,'other source families'):self.console_check(evidence=evidence)

    def test_console_test_helper_fixture_and_core_source_drift_rejected(self):
        for key in ('test_sources','fixtures_and_original_sources'):
            evidence=copy.deepcopy(self.evidence);evidence[key][next(iter(evidence[key]))]='0'*64
            with self.assertRaisesRegex(ValueError,'source/hash drift'):self.console_check(evidence=evidence)
        evidence=copy.deepcopy(self.evidence);evidence['core_package']['source_bindings'][next(iter(evidence['core_package']['source_bindings']))]='0'*64
        with self.assertRaisesRegex(ValueError,'source/hash drift'):self.console_check(evidence=evidence)

    def test_console_duplicate_lost_case_error_and_raw_report_drift_rejected(self):
        for mode in ('duplicate','lost','error'):
            saved=copy.deepcopy(self.execution);report=saved['reports'][0];tree=ET.fromstring(report['xml_without_hostname']);cases=tree.findall('testcase')
            if mode=='duplicate':cases[0].set('name',cases[1].get('name'));cases[0].set('classname',cases[1].get('classname'))
            elif mode=='lost':tree.remove(cases[0]);tree.set('tests','8')
            else:ET.SubElement(cases[0],'error')
            report['xml_without_hostname']=ET.tostring(tree,encoding='unicode');report['xml_without_hostname_sha256']=hashlib.sha256(report['xml_without_hostname'].encode()).hexdigest()
            with self.assertRaises(ValueError):f.validate_execution(saved,self.rows)
        evidence=copy.deepcopy(self.evidence);evidence['raw_jupiter_report']['xml']+='changed'
        with self.assertRaisesRegex(ValueError,'raw XML hash drift'):self.console_check(evidence=evidence)

    def test_console_original_registration_ordinal_join_cannot_change(self):
        for mode in ('ordinal','original','result'):
            evidence=copy.deepcopy(self.evidence);row=evidence['nine_results'][0]
            row[{'ordinal':'ordinal','original':'originalRuntimeID','result':'result'}[mode]]=999 if mode=='ordinal' else 'changed'
            with self.assertRaisesRegex(ValueError,'nine-way join'):self.console_check(evidence=evidence)

    @staticmethod
    def archive_record(entries):
        buffer=io.BytesIO()
        with zipfile.ZipFile(buffer,'w',zipfile.ZIP_DEFLATED) as archive:
            for name,raw in entries.items():archive.writestr(name,raw)
        raw=buffer.getvalue()
        return dict(base64=base64.b64encode(raw).decode(),sha256=hashlib.sha256(raw).hexdigest())

    def test_grouped_reviewer_substitutions_are_rejected_without_execution(self):
        # Exact reviewer counterexamples. Substituted commands/bytecode are ONLY
        # data passed to validation, never spawned, loaded or executed.
        for mode in ('fake-executable','rehashed-test-bytecode','rehashed-production-bytecode'):
            with self.subTest(mode=mode):
                evidence=copy.deepcopy(self.evidence);saved=copy.deepcopy(self.execution)
                if mode=='fake-executable':
                    evidence['run_command'][0]='/bin/true';saved['actual_command_args']=evidence['run_command']
                elif mode=='rehashed-test-bytecode':
                    entries=f._archive(evidence['test_jar']);name=next(n for n in entries if n.endswith('FB2ReaderTest.class'))
                    entries[name]=b'not executable class bytecode';evidence['test_jar']=self.archive_record(entries)
                else:
                    core=evidence['core_package'];entries=f._archive(core['selected_class_zip']);name=next(iter(core['selected_classes']))
                    entries[name]=b'changed production';core['selected_class_zip']=self.archive_record(entries)
                    core['selected_classes'][name]=core['classes'][name]=hashlib.sha256(entries[name]).hexdigest()
                with self.assertRaisesRegex(ValueError,'identity differs'):
                    self.console_check(evidence=evidence,saved=saved)

    def test_grouped_toolchain_dependency_and_classmap_resealing_is_not_authority(self):
        for mode in ('run-and-tool','compile-and-tool','dependency','unselected-class-map','tool-binary-identity'):
            with self.subTest(mode=mode):
                evidence=copy.deepcopy(self.evidence);saved=copy.deepcopy(self.execution)
                if mode=='run-and-tool':
                    evidence['run_command'][0]=evidence['toolchain']['runtime']['path']='/bin/true'
                    saved['actual_command_args']=evidence['run_command']
                elif mode=='compile-and-tool':
                    evidence['compile_command'][0]=evidence['toolchain']['compiler']['path']='/bin/true'
                elif mode=='dependency':
                    name=next(n for n in evidence['dependencies'] if 'serialization-json' in n)
                    evidence['dependencies'][name]='0'*64
                elif mode=='unselected-class-map':
                    core=evidence['core_package'];name=next(n for n in core['classes'] if n not in core['selected_classes']);core['classes'][name]='0'*64
                else:evidence['toolchain']['runtime']['sha256']='0'*64
                with self.assertRaisesRegex(ValueError,'reviewed artifact/tool identity differs'):
                    self.console_check(evidence=evidence,saved=saved)

    def test_console_verification_does_not_require_original_tmp_artifacts(self):
        original=Path.read_bytes
        def portable(path):
            if str(path).startswith(('/tmp/','/private/tmp/')):
                raise AssertionError('Verifier tried to read nonportable runtime artifact: '+str(path))
            return original(path)
        with patch.object(Path,'read_bytes',new=portable):
            self.assertIs(f.validate_execution(self.execution,self.rows),self.execution)

    def test_historical_gradle_mode_preserves_original_result_and_rejects_selector_loss(self):
        old=json.loads(self.history['old_execution_json']);self.assertEqual(old['result'],self.execution['result'])
        self.assertNotEqual(old['relevant_sources_before_and_after']['core/src/main/kotlin/io/heapy/kotdoc/ast/Builder.kt'],self.execution['relevant_sources_before_and_after']['core/src/main/kotlin/io/heapy/kotdoc/ast/Builder.kt'])
        # Historical validator exercises the original artifact's exact source identities,
        # not a bypass for current source drift or a rewrite of the historical data.
        with patch.object(f,'source',side_effect=lambda path:dict(path=path,sha256=old['relevant_sources_before_and_after'][path])):
            self.assertIs(f.validate_execution(old,self.rows),old)
            changed=copy.deepcopy(old);changed['actual_shared_command']='no selectors'
            with self.assertRaisesRegex(ValueError,'selectors missing'):f.validate_execution(changed,self.rows)

    def test_historical_failed_checker_and_xml_are_immutable_not_promoted(self):
        self.assertEqual(self.history['later_failed_validation']['exit'],1)
        self.assertEqual(self.history['later_failed_validation']['executed_inventory_tests'],0)
        for mode in ('xml','failure','old-json'):
            history=copy.deepcopy(self.history)
            if mode=='xml':history['raw_reports'][0]['xml']+='changed'
            elif mode=='failure':history['later_failed_validation']['exit']=0
            else:history['old_execution_json']+='changed'
            with self.assertRaises(ValueError):self.console_check(history=history)


if __name__=='__main__':unittest.main()
