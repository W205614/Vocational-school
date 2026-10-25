"""Redis logical equivalence is separate from nondeterministic RDB serialization."""
import base64
import copy
import importlib.util
import json
from pathlib import Path
import unittest
from recovery_upgrade import compare_redis_snapshots

module_path=Path(__file__).resolve().parents[1]/'final'/'redis_snapshot.py'
spec=importlib.util.spec_from_file_location('snapshot_semantics',module_path)
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
namespace={'base64':base64,'json':json};exec(module.SEMANTIC_SCRIPT,namespace)
fingerprint=namespace['semantic_fingerprint']

class RedisSemanticSnapshotTest(unittest.TestCase):
 def snapshot(self,kind='hash',values=None,dump='original-dump',expiry=10000):
  values=values if values is not None else [b'\x00field',b'\xffvalue',b'other',b'two']
  return {'format':2,'records':[{'db':0,'key':'a2V5','value':dump,'expiresAt':expiry,'type':kind,'semanticSha256':fingerprint(kind,values)}]}
 def test_collection_order_is_canonical_but_list_order_and_binary_values_are_significant(self):
  self.assertEqual(fingerprint('hash',[b'a',b'1',b'\xff',b'\x00']),fingerprint('hash',[b'\xff',b'\x00',b'a',b'1']))
  self.assertEqual(fingerprint('set',[b'\xff',b'a']),fingerprint('set',[b'a',b'\xff']))
  self.assertEqual(fingerprint('zset',[b'b',b'2.0',b'a',b'1']),fingerprint('zset',[b'a',b'1.0',b'b',b'2']))
  self.assertNotEqual(fingerprint('list',[b'a',b'b']),fingerprint('list',[b'b',b'a']))
  self.assertNotEqual(fingerprint('string',[b'\xff']),fingerprint('string',[b'\xfe']))
  self.assertNotEqual(fingerprint('hash',[b'a',b'1']),fingerprint('hash',[b'a',b'2']))
 def test_semantically_equal_hash_accepts_different_dump_bytes(self):
  expected=self.snapshot();restored=copy.deepcopy(expected);restored['records'][0]['value']='reordered-dump'
  proof=compare_redis_snapshots(expected,restored,1000)
  self.assertEqual('semantic',proof['redisComparison']);self.assertEqual(1,proof['redisReserializedRecords'])
 def test_type_value_expiry_missing_extra_and_invalid_fingerprints_still_fail(self):
  expected=self.snapshot()
  for change in ('type','value','expiry','missing','extra','invalid','duplicate'):
   actual=copy.deepcopy(expected)
   if change=='type':actual['records'][0]['type']='set'
   if change=='value':actual['records'][0]['semanticSha256']='0'*64
   if change=='expiry':actual['records'][0]['expiresAt']+=2000
   if change=='missing':actual['records']=[]
   if change=='extra':actual['records'].append({**actual['records'][0],'key':'ZXh0cmE='})
   if change=='invalid':actual['records'][0].pop('semanticSha256')
   if change=='duplicate':actual['records'].append(dict(actual['records'][0]))
   with self.subTest(change=change),self.assertRaises(RuntimeError):compare_redis_snapshots(expected,actual,1000)
 def test_legacy_or_mixed_format_remains_strict_dump_comparison(self):
  expected={'format':1,'records':[{'db':0,'key':'a2V5','value':'original-dump','expiresAt':10000}]}
  restored=self.snapshot();self.assertEqual('dump-bytes',compare_redis_snapshots(expected,restored,1000)['redisComparison'])
  restored['records'][0]['value']='different-dump'
  with self.assertRaises(RuntimeError):compare_redis_snapshots(expected,restored,1000)
 def test_natural_expiry_is_allowed_for_v2_and_persistent_ttl_must_stay_persistent(self):
  expired=self.snapshot(expiry=100);self.assertEqual(1,compare_redis_snapshots(expired,{'format':2,'records':[]},1000)['redisExpiredRecordsSkipped'])
  persistent=self.snapshot(expiry=None);changed=copy.deepcopy(persistent);changed['records'][0]['expiresAt']=10000
  with self.assertRaises(RuntimeError):compare_redis_snapshots(persistent,changed,1000)

if __name__=='__main__':unittest.main()
