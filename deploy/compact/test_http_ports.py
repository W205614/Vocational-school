import unittest
from http_ports import listener_sysctls

class ListenerReservationTest(unittest.TestCase):
    def test_all_four_nested_call_listeners_are_reserved(self):
        self.assertEqual({'net.ipv4.ip_local_reserved_ports':'38002,39002,39003,39004,39005'}, listener_sysctls(38002,39002))
        self.assertEqual({'net.ipv4.ip_local_reserved_ports':'38410'}, listener_sysctls(38410))
        with self.assertRaises(ValueError): listener_sysctls(65000,65533)

if __name__ == '__main__': unittest.main()
