#!/usr/bin/env python3
"""Focused root-reconciliation regressions; no upstream tests are executed."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import hashlib
from unittest.mock import patch
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('dependencies',ROOT/'tools/dependency-test-inventory.py')
d=importlib.util.module_from_spec(spec);spec.loader.exec_module(d)


class DependencyInventoryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.capture=json.loads(d.CAPTURE.read_text())
        cls.data=d.inventory()
        cls.pss=cls.data['accepted_module_assertion_mappings'][0]

    def test_complete_retained_cabal_roots_and_units(self):
        self.assertEqual(len(self.data['roots']),118)
        self.assertEqual(len(self.capture['packages']),76)
        self.assertEqual(len(self.data['cabal_test_suites']),69)
        for row in self.data['cabal_test_suites']:
            self.assertEqual(row['status'],'unmapped')
            self.assertIsNone(row['kotlin'])
            self.assertEqual(row['execution_units']['declared'],1)
            for key in ('listed','runtime','assertions'):self.assertIsNone(row['execution_units'][key])

    def test_omitted_duplicate_or_invented_manifest_rejected(self):
        for mode in ('omit','duplicate','invent'):
            value=copy.deepcopy(self.capture)
            if mode=='omit':value['packages'].pop()
            elif mode=='duplicate':value['packages'].append(value['packages'][0])
            else:value['packages'][0]['path']='vendor/not-retained/invented.cabal'
            with self.assertRaisesRegex(ValueError,'package root'):d.validate_capture(value)

    def test_lost_suite_or_executable_rejected(self):
        for field in ('test_suites','executables'):
            value=copy.deepcopy(self.capture)
            next(p for p in value['packages'] if p[field])[field].pop()
            with self.assertRaisesRegex(ValueError,'declaration reconciliation'):d.validate_capture(value)

    def test_source_hash_and_parser_drift_rejected(self):
        value=copy.deepcopy(self.capture);value['sources'][0]['sha256']='0'*64
        with self.assertRaisesRegex(ValueError,'source inventory/hash'):d.validate_capture(value)
        value=copy.deepcopy(self.capture);value['parser']='Cabal-unknown'
        with self.assertRaisesRegex(ValueError,'parser profile'):d.validate_capture(value)

    def test_original_cabal_common_import_and_both_nested_branches(self):
        row=json.loads((ROOT/'tools/test-inventory-dependency-reference/fixtures/conditional.json').read_text())[0]
        ts=row['test_suites'][0]['tree']
        self.assertEqual(ts['data']['source_dirs'],['source-common'])
        self.assertEqual(ts['data']['entry'],dict(kind='main-is',value='Main.hs'))
        nodes=list(d.nodes(ts))
        self.assertTrue(any(data['source_dirs']==['alternate'] for _,data in nodes))
        self.assertTrue(any(data['source_dirs']==['ordinary'] for _,data in nodes))
        self.assertTrue(any(not data['buildable'] and len(conditions)==2 for conditions,data in nodes))
        self.assertTrue(any(data['cpp_options']==['-DNOT_WINDOWS'] for _,data in nodes))
        self.assertEqual(row['executables'][0]['name'],'separate-runner')

    def test_entry_resolution_missing_and_escaping_are_explicit(self):
        base=ROOT/'tools/test-inventory-dependency-reference/fixtures'
        self.assertEqual(d.resolve_paths(base,['absent'],'Main.hs')[0]['availability'],'missing-source')
        self.assertEqual(d.resolve_paths(base,['../../../../../'],'outside.hs')[0]['availability'],'outside-retained-package')

    def test_main_strings_comments_and_setup_are_not_suite_counts(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'sample.hs'
            p.write_text('-- main = fake\n{-\nmain = fake\n-}\nx = "main = fake"\nmain :: IO ()\nmain = pure ()\n')
            signals=d.source_signals(p)
            self.assertEqual([x['line'] for x in signals],[6,7])
        self.assertTrue(any(r['role']=='package-setup' for r in self.data['embedded_and_main_signals']))

    def test_no_manifest_or_frozen_source_does_not_mean_no_tests(self):
        absent=[r for r in self.data['roots'] if not r['package_manifests']]
        self.assertEqual(len(absent),42)
        self.assertTrue(all('unknown' in r['gap'] for r in absent))
        missing=[r for r in self.data['frozen_dependencies'] if not r['matching_retained_manifests']]
        self.assertTrue(missing)
        self.assertTrue(all(r['availability']=='no-exact-retained-manifest' for r in missing))

    def test_reviewed_non_cabal_routes_preserve_aliases_and_dispatch(self):
        rows=self.data['reviewed_non_cabal_routes']
        self.assertEqual(len(rows),4)
        self.assertIn('alias route',rows[0]['execution_conditions'])
        self.assertIn('ghc-7.0.0.20100924',rows[1]['execution_conditions'])
        self.assertIn('otherwise benchmark',rows[3]['registration_semantics'])
        for row in rows:
            self.assertEqual(row['status'],'unmapped')
            self.assertIsNone(row['execution_units']['assertions'])

    def test_only_active_shell_assertion_sites_preserve_original_integers(self):
        sites=self.data['reviewed_non_cabal_routes'][2]['literal_assertion_sites']
        self.assertEqual([(s['rule'],s['expected_firings']) for s in sites],[('TEXT literal',8),('TEXT literal UTF8',7),('TEXT empty literal',4)])
        self.assertTrue(all(s['status']=='unmapped' and s['kotlin'] is None for s in sites))

    def test_source_identity_not_just_suite_name(self):
        suites=self.data['cabal_test_suites']
        self.assertEqual(len({s['id'] for s in suites}),len(suites))
        self.assertGreater(len([s for s in suites if s['name']=='spec']),1)

    def pss_check(self,value):
        return d.validate_pss_mapping(value,self.data['cabal_test_suites'])

    def test_pss53_original_source_order_and_storage_order_are_distinct_bijections(self):
        rows=self.pss['registrations']
        self.assertEqual(len(rows),53);self.assertEqual(len({r['id'] for r in rows}),53)
        self.assertEqual([r['original_source_ordinal'] for r in rows],list(range(1,54)))
        self.assertEqual({r['corpus_index'] for r in rows},set(range(53)))
        self.assertEqual([r['corpus_index'] for r in rows[:5]],[50,51,52,0,1])
        self.assertEqual(sum(r['expansion']['helper']=='doSignTest' for r in rows),25)
        self.assertEqual(sum(r['expansion']['helper']=='doVerifyTest' for r in rows),25)
        self.assertEqual(sum(r['expansion']['helper']=='signatureRangeTests' for r in rows),3)
        self.assertEqual({r['kotlin']['display_name'] for r in rows},{f'SourceCases/{i}' for i in range(53)})
        self.assertEqual(self.pss['proof']['junit']['full_result']['tests'],10136)
        self.assertEqual(self.pss['proof']['junit']['selected_result']['tests'],53)

    def test_pss_does_not_promote_root_or_add_pandoc_runtime_cases(self):
        self.assertEqual(len(self.data['frozen_dependencies']),226)
        self.assertEqual(len(self.data['cabal_test_suites']),69)
        self.assertTrue(all(r['status']=='unmapped' and r['kotlin'] is None for r in self.data['cabal_test_suites']))
        snapshot=json.loads((ROOT/'docs/test-inventory.json').read_text());tasty=snapshot['pandoc_runtime']['leaves']
        self.assertEqual(len(tasty),4465)
        self.assertFalse({r['id'] for r in self.pss['registrations']} & {r['id'] for r in tasty})
        self.assertFalse(self.pss['counting_units']['whole_crypton_suite_complete'])
        self.assertEqual(self.pss['counting_units']['new_pandoc_tasty_leaves'],0)
        self.assertFalse(any('CryptoRsaTypes' in p for p in self.pss['proof']['source_bindings']))

    def test_pss_lost_duplicate_reordered_or_renamed_original_registration_rejected(self):
        for mode in ('lost','duplicate','storage-order','component','occurrence','bool-ordinal'):
            with self.subTest(mode=mode):
                value=copy.deepcopy(self.pss);rows=value['registrations']
                if mode=='lost':rows.pop()
                elif mode=='duplicate':rows[1]=copy.deepcopy(rows[0])
                elif mode=='storage-order':rows.sort(key=lambda r:r['corpus_index'])
                elif mode=='component':rows[0]['components'][-1]='changed'
                elif mode=='occurrence':rows[0]['occurrence']=2
                else:rows[0]['original_source_ordinal']=True
                with self.assertRaises(ValueError):self.pss_check(value)

    def test_pss_factory_corpus_ordinal_and_independent_expected_identity_rejected(self):
        for mode in ('factory','ordinal','label','expected','corpus-row'):
            value=copy.deepcopy(self.pss);row=value['registrations'][0]
            if mode=='factory':row['kotlin']['factory']='ordinary'
            elif mode=='ordinal':row['kotlin']['dynamic_ordinal']=1
            elif mode=='label':row['source_case']='internal/sign/0'
            elif mode=='expected':row['expected_sha256']='0'*64
            else:row['corpus_row_sha256']='0'*64
            with self.assertRaises(ValueError):self.pss_check(value)

    def test_pss_current_source_drift_is_not_rebound_to_historical_artifact(self):
        original=d.source;target='core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoRsaPss.kt'
        def changed(path):
            row=original(path)
            if str(path)==target:row['sha256']='0'*64
            return row
        with patch.object(d,'source',side_effect=changed):
            with self.assertRaisesRegex(ValueError,'source hash drift'):self.pss_check(self.pss)

    def test_pss_independent_artifact_and_result_pins_reject_resealed_metadata(self):
        # Historical commands/classes are metadata only; no substitution executes.
        for mode in ('core-sha','class-map','command','full-xml-sha'):
            value=copy.deepcopy(self.pss);proof=value['proof']
            if mode=='core-sha':proof['core_artifact']['sha256']='0'*64
            elif mode=='class-map':proof['core_artifact']['production_classes'][next(iter(proof['core_artifact']['production_classes']))]='0'*64
            elif mode=='command':proof['actual_command']='/bin/true'
            else:proof['junit']['full_report_sha256']='0'*64
            with self.assertRaises(ValueError):self.pss_check(value)

    def test_pss_xml_case_count_class_failure_and_original_join_rejected(self):
        for mode in ('lost','duplicate','class','failure','row-join'):
            value=copy.deepcopy(self.pss);report=value['proof']['junit']
            if mode=='lost':report['raw_cases'].pop()
            elif mode=='duplicate':report['raw_cases'][1]=report['raw_cases'][0]
            elif mode=='class':report['raw_cases'][0]=report['raw_cases'][0].replace('CryptoRsaPssTest','UnrelatedTest')
            elif mode=='failure':report['raw_cases'][0]=report['raw_cases'][0].replace('/>','><failure/></testcase>')
            else:value['registrations'][0]['execution_case_xml']=report['raw_cases'][0]
            with self.assertRaises(ValueError):self.pss_check(value)

    def test_pss_subset_cannot_be_counted_as_complete_crypton_or_full_junit(self):
        for mode in ('count','complete','full-result','scope'):
            value=copy.deepcopy(self.pss)
            if mode=='count':value['counting_units']['original_source_assertions']=106
            elif mode=='complete':value['counting_units']['whole_crypton_suite_complete']=True
            elif mode=='scope':value['scope']='all crypton ported'
            else:value['proof']['junit']['full_result']['tests']=53
            with self.assertRaises(ValueError):self.pss_check(value)
        suites=copy.deepcopy(self.data['cabal_test_suites']);root=next(r for r in suites if r['id']==d.PSS_SUITE);root['status']='mapped'
        with self.assertRaisesRegex(ValueError,'entire crypton'):d.validate_pss_mapping(self.pss,suites)

    def test_pss_portable_proof_needs_no_original_tmp_jar_xml_or_executable(self):
        original=Path.read_bytes
        def portable(path):
            if str(path).startswith(('/tmp/','/private/tmp/')):raise AssertionError('Nonportable PSS proof read: '+str(path))
            return original(path)
        with patch.object(Path,'read_bytes',new=portable):self.assertIs(self.pss_check(self.pss),self.pss)


if __name__=='__main__':unittest.main()
