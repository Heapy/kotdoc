#!/usr/bin/env python3
"""Finite nine-leaf source/execution bridge regressions; no conversion execution."""
import copy
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


if __name__=='__main__':unittest.main()
