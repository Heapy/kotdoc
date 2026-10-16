#!/usr/bin/env python3
"""Independent inventory-link regressions; no Gradle/original test execution."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('types_inventory',ROOT/'tools/pandoc-types-test-inventory.py')
t=importlib.util.module_from_spec(spec);spec.loader.exec_module(t)


class TypesInventoryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data=t.inventory();cls.rows={r['id']:r for r in cls.data['registrations']}

    def test_exact81_join_and_distinct_counting_units(self):
        self.assertEqual(len(self.rows),81)
        self.assertEqual(self.data['assertion_property_registrations'],137)
        self.assertEqual(self.data['finite_kotlin_behavior_identities'],20419)
        self.assertEqual(self.data['original_suite_reports']['count'],11)
        self.assertEqual(self.data['result']['recorded_junit_tests'],32056)
        self.assertFalse(self.data['fresh_execution'])
        self.assertTrue(all(r['status']=='mapped' and r['kotlin']['assertion_contract'] and r['production'] and r['expectation_origin'] for r in self.rows.values()))

    def test_original_literal_display_labels_keep_ids_and_symbols(self):
        for symbol,label in [('t_tableSan','table sanitisation'),('t_tableNormExample','table normalization example')]:
            row=self.rows['pandoc-types/literal/'+symbol]
            self.assertEqual(row['id'],'pandoc-types/literal/'+symbol)
            self.assertEqual(row['expression'],symbol)
            self.assertEqual(row['name'],label)

    def test_manifest_join_missing_extra_or_renamed_id_fails_closed(self):
        rows=list(self.rows.values());mapping={r['id']:r['manifest_mapping'] for r in rows}
        for change in ('missing','extra','rename'):
            candidate=dict(mapping)
            if change=='missing':candidate.pop(next(iter(candidate)))
            elif change=='extra':candidate['invented']='invented'
            else:candidate['invented']=candidate.pop(next(iter(candidate)))
            with self.assertRaisesRegex(ValueError,'registration join'):t.validate_join(rows,candidate)

    def test_list_property_uses_actual_nonlist_display_name_and_factory_ordinals(self):
        row=self.rows['pandoc-types/property/p_walkList inlinesTrans']
        ids=row['kotlin']['identities']
        self.assertEqual(len(ids),500)
        self.assertEqual(ids[0]['display_name'],'p_walk inlinesTrans seed=1 size=1')
        self.assertEqual([i['dynamic_ordinal'] for i in ids[:3]],[5,17,29])
        query=self.rows['pandoc-types/property/p_queryList blocksQuery']['kotlin']['identities']
        self.assertEqual(query[0]['dynamic_ordinal'],12)

    def test_table_actual_flatmap_order_and_original_domain(self):
        head=self.rows['pandoc-types/property/p_tableNormHeadIdempotent']
        body=self.rows['pandoc-types/property/p_tableNormBodyIsSubset']
        self.assertEqual(len(head['kotlin']['identities']),1500)
        self.assertEqual(head['kotlin']['identities'][0]['display_name'],'p_tableNormHeadIdempotent width=2 seed=1')
        self.assertEqual(body['kotlin']['identities'][0]['dynamic_ordinal'],5)
        self.assertEqual(head['original_generator']['instance']['type'],'TableHead')
        self.assertIn('expected',head['expectation_origin']['expected_fields'])

    def test_figure_jsonprimitive_witness_quotes_are_preserved(self):
        row=self.rows['pandoc-types/property/p_figureRepresentation figure representation']
        names=[i['display_name'] for i in row['kotlin']['identities']]
        self.assertEqual(len(names),305)
        self.assertIn('p_figureRepresentation figure representation seed=0 size=0 witness="empty"',names)
        self.assertTrue(names[0].endswith('witness=""'))

    def test_paired_json_assertions_have_independent_bytes_and_typed_values(self):
        row=self.rows['pandoc-types/json/t_meta']
        self.assertEqual([r['display_name'] for r in row['kotlin']['identities']],['t_meta encoding','t_meta decoding'])
        self.assertTrue(row['expectation_origin']['expected_json_sha256'])
        self.assertTrue(row['expectation_origin']['typed_expression_sha256'])
        self.assertIsNone(row['original_generator'])
        self.assertEqual(row['production'][0]['entrypoints'],['encodeMeta','decodeMeta'])

    def test_missing_executable_contract_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'assertion contract missing'):t.require('fun factory() {}',['assertEquals(independent,actual)'])
        with self.assertRaisesRegex(ValueError,'method missing'):t.kotlin_factory('OriginalTypesPropertiesTest','inventedFactory',[])

    def test_sources_and_payload_hashes_fail_closed(self):
        with self.assertRaisesRegex(ValueError,'source hash differs'):t.validate_sources({'sources':{t.PT:'0'*64}},t.COMMIT)
        manifest=json.loads(t.blob(t.MANIFEST));manifest['payloads']['remaining-properties']='0'*64
        with self.assertRaisesRegex(ValueError,'payload hash differs'):t.payload('remaining-properties',manifest)

    def test_all_historical_artifacts_exclude_checkout_read_wip(self):
        for row in self.rows.values():
            self.assertEqual(row['kotlin']['source']['recorded_commit'],t.COMMIT)
            self.assertEqual(row['result_ref'],t.COMMIT)
        self.assertEqual([r['result']['recorded_junit_tests'] for r in self.data['support_checkpoints']],[33284,33466])
        self.assertNotIn('PandocRead.kt',json.dumps(self.data))
        self.assertNotIn('read-manifest.json',json.dumps(self.data))


if __name__=='__main__':unittest.main()
