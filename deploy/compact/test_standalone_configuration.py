import unittest
from benchmark_deployment import standalone_configuration


class StandaloneBootPolicyTest(unittest.TestCase):
    def test_original_cycle_policy_and_actual_health_contributors_are_retained(self):
        config={'spring':{'main':{'allow-bean-definition-overriding':False}},'management':{'endpoint':{'health':{'group':{'readiness':{'include':'readinessState,redis,db'}}}}}}
        source='spring:\n  main:\n    allow-circular-references: true\nmanagement:\n  endpoint:\n    health:\n      group:\n        readiness:\n          include: readinessState,db\n'
        value=standalone_configuration(config,source)
        self.assertTrue(value['spring']['main']['allow-circular-references'])
        self.assertFalse(value['spring']['main']['allow-bean-definition-overriding'])
        self.assertEqual('readinessState,db',value['management']['endpoint']['health']['group']['readiness']['include'])
        with self.assertRaises(RuntimeError):standalone_configuration(config,'spring: {}')


if __name__=='__main__':unittest.main()
