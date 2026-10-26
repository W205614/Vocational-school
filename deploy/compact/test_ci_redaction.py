import unittest
from ci_redaction import redact,excerpt


class CiRedactionTest(unittest.TestCase):
    def test_known_values_and_jwt_are_removed_without_losing_safe_diagnostics(self):
        value = redact('healthy\nvalue=known-secret-value\ntoken=eyJhbGc.abc.def\nready', {'known-secret-value'})
        self.assertNotIn('known-secret-value', value)
        self.assertNotIn('eyJhbGc', value)
        self.assertIn('healthy', value)
        self.assertIn('ready', value)

    def test_playwright_dialog_quotes_cannot_export_random_temporary_credentials(self):
        temporary = 'Synthetic_receipt_1234567890_AaBbCc'
        value = redact('TimeoutError: clipboard denied\nReceived string: 临时密码：'+temporary+'到期时间：tomorrow\n63 passed')
        self.assertNotIn(temporary, value)
        self.assertIn('clipboard denied', value)
        self.assertIn('63 passed', value)

    def test_structured_password_and_continuation_receipt_lines_are_excluded(self):
        temporary = 'Synthetic_receipt_1234567890_AaBbCc'
        value = redact('temporaryPassword: '+temporary+'\n'+temporary+'到期时间：tomorrow\nready')
        self.assertEqual('ready', value)

    def test_excerpt_scrubs_before_tail_can_cut_off_the_credential_label(self):
        temporary = 'Synthetic_receipt_1234567890_AaBbCc'
        value = excerpt('Received: 临时密码：'+temporary+'到期时间：tomorrow\nready', limit=60)
        self.assertNotIn(temporary, value)
        self.assertEqual('ready', value)

    def test_authenticator_receipts_and_bare_verification_codes_do_not_leave_public_diagnostics(self):
        key='JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP'
        value=redact('验证器密钥: '+key+'\n'+key+'\n验证码\n123456\nready')
        self.assertNotIn(key,value)
        self.assertNotIn('123456',value)
        self.assertIn('ready',value)


if __name__ == '__main__':
    unittest.main()
