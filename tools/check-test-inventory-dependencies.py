#!/usr/bin/env python3
"""Focused root-reconciliation regressions; no upstream tests are executed."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('dependencies',ROOT/'tools/dependency-test-inventory.py')
d=importlib.util.module_from_spec(spec);spec.loader.exec_module(d)


class DependencyInventoryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.capture=json.loads(d.CAPTURE.read_text())
        cls.data=d.inventory()

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


if __name__=='__main__':unittest.main()
