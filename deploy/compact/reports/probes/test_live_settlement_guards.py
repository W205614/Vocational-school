"""Only validates isolation predicates; this does not claim the live business probe passed."""
import unittest
from copy import deepcopy
from types import SimpleNamespace
from unittest.mock import patch
import run_live_settlement as probe

class IsolationGuards(unittest.TestCase):
    def test_primary_deployment_is_rejected(self):
        with self.assertRaises(RuntimeError):probe.isolation(probe.SOURCE,'tianji-compact')
    def test_recovery_project_cannot_point_at_primary_home(self):
        with self.assertRaises(RuntimeError):probe.isolation(probe.SOURCE,'tianji-recovery-upgrade-20261015b')
    def test_arbitrary_ignored_home_is_rejected(self):
        with self.assertRaises(RuntimeError):probe.isolation(probe.ROOT/'.local/other-project','tianji-recovery-test')
    def test_primary_project_cannot_use_clone_home(self):
        with self.assertRaises(RuntimeError):probe.isolation(probe.ROOT/'.local/upgrade-rehearsal-20261015b','tianji-compact')
    def test_missing_approved_home_is_rejected(self):
        with patch.object(probe.Path,'is_dir',return_value=False):
            with self.assertRaises(RuntimeError):probe.isolation(probe.ROOT/'.local/final-upgrade-test','tianji-recovery-test')
    def test_expected_home_shape_can_pass_structural_guards(self):
        with patch.object(probe.Path,'is_dir',return_value=True),patch.object(probe.Path,'is_file',return_value=True),patch.object(probe.Path,'is_symlink',return_value=False):
            probe.isolation(probe.ROOT/'.local/final-upgrade-test','tianji-recovery-test')
    def test_expected_role_browser_counts_and_freshness_pass(self):
        probe.validate_browser({'status':'PASSED','passed':42,'failed':0,'skipped':30},11,[10]*7)
    def test_older_suite_cannot_be_mistaken_for_final_browser(self):
        with self.assertRaises(RuntimeError):probe.validate_browser({'status':'PASSED','passed':41,'failed':0,'skipped':29},11,[10]*7)
    def test_stale_browser_cannot_authorize_new_containers(self):
        with self.assertRaises(RuntimeError):probe.validate_browser({'status':'PASSED','passed':42,'failed':0,'skipped':30},9,[10]*7)

class RewardPurchaseGuards(unittest.TestCase):
    def test_coupon_fixture_name_is_unique_and_within_actual_dto_size(self):
        # Read the real Bean Validation declaration so a future DTO change fails this preparation guard.
        source=(probe.ROOT/'tj-promotion/src/main/java/com/tianji/promotion/domain/dto/CouponFormDTO.java').read_text(encoding='utf8')
        declaration=probe.re.search(r'@Size\(([^)]*)\)\s+private String name;',source)
        self.assertIsNotNone(declaration)
        lower=int(probe.re.search(r'min\s*=\s*(\d+)',declaration[1])[1]);upper=int(probe.re.search(r'max\s*=\s*(\d+)',declaration[1])[1])
        names=[]
        for run in ['0'*32,'f'*32,'123456789abcdef0123456789abcdef0']:
            name=probe.coupon_fixture_form(run)['name'];self.assertGreaterEqual(len(name),lower);self.assertLessEqual(len(name),upper);names.append(name)
        self.assertEqual(3,len(set(names)))
        self.assertGreater(len('Live settle '+'0'*12),upper)  # Preserve the actual failed input regression.
    def test_coupon_fixture_rejects_non_uuid_identity(self):
        for value in ['', 'x'*32, 'a'*31, 'a'*33]:
            with self.assertRaises(RuntimeError):probe.coupon_fixture_form(value)
    def quote(self):
        return dict(orderId='40',totalAmount=100,courses=[dict(id='7')],availableCoupons=[dict(id='11',couponId='12',name='earned',termEndTime='2031-01-01T00:00:00')],discounts=[dict(ids=['11'],discountAmount=10)])
    def test_explicit_reward_ticket_quote_passes(self):
        self.assertEqual('11',probe.reward_quote(self.quote(),11,12,'earned')['id'])
    def test_other_owned_ticket_cannot_replace_the_reward(self):
        quote=self.quote();quote['discounts'][0]['ids']=['99']
        with self.assertRaises(RuntimeError):probe.reward_quote(quote,11,12,'earned')
    def test_degraded_or_unresolved_quote_cannot_purchase(self):
        for value in [dict(discountError='unavailable'),dict(couponSelectionRequired=True)]:
            quote=self.quote();quote.update(value)
            with self.assertRaises(RuntimeError):probe.reward_quote(quote,11,12,'earned')
    def test_wrong_discount_or_zero_payment_cannot_purchase(self):
        for total,discount in [(100,0),(100,9),(10,10)]:
            quote=self.quote();quote['totalAmount']=total;quote['discounts'][0]['discountAmount']=discount
            with self.assertRaises(RuntimeError):probe.reward_quote(quote,11,12,'earned')
    def fixture(self,payment='SIMULATED'):
        calls=[];observations=[]
        def call(session,method,path,body=None,key=None):
            calls.append((method,path,deepcopy(body),key))
            if path=='/environment':return dict(payment=payment)
            if path=='/services/trade/pay/channels':return [dict(channelCode='mockPay')]
            if path.startswith('/orders/confirmation?'):return self.quote()
            if path=='/orders':return dict(operationId='op',status='PENDING')
            if path=='/operations/trade/op':return dict(status='SUCCEEDED')
            if path=='/services/trade/pay/order':return '/simulate-payment/40'
            if path=='/simulator/payments/40/confirm':return dict(status='PAID',mode='SIMULATED')
            if path=='/orders/40':return dict(status=2,totalAmount=100,realAmount=90,discountAmount=10,details=[dict(id='41',courseId='7',realPayAmount=90)])
            raise AssertionError(path)
        def query(db,sql):
            self.assertTrue(sql.startswith('SELECT '));observations.append((db,sql))
            if sql.startswith('SELECT c.id FROM course'):return '7'
            if sql.startswith('SELECT DATE_FORMAT'):return '2031-01-01 00:00:00'
            if sql.startswith('SELECT id FROM order_detail'):return '41'
            return '1'
        def identifier(v):
            if not str(v).isdigit() or int(v)<=0:raise ValueError('Invalid fixture identifier')
            return str(v)
        return SimpleNamespace(gateway_call=call,identifier=identifier),query,calls,observations
    def test_real_provider_mode_rejected_before_any_order_command(self):
        fixture,query,calls,_=self.fixture(payment='CONFIGURED')
        with self.assertRaises(RuntimeError):probe.purchase_reward(fixture,None,1,11,12,'earned',7,query,lambda p,m:self.assertTrue(p()))
        self.assertEqual([('GET','/environment')],[(m,p) for m,p,_,_ in calls])
    def test_missing_safe_course_rejected_before_any_order_command(self):
        fixture,query,calls,_=self.fixture()
        with self.assertRaises(ValueError):probe.purchase_reward(fixture,None,1,11,12,'earned',7,lambda db,sql:'',lambda p,m:self.assertTrue(p()))
        self.assertFalse(any(m=='POST' for m,_,_,_ in calls))
    def test_orchestration_keeps_exact_ticket_and_simulator_with_readonly_observations(self):
        # Contract doubles only: this verifies runner orchestration, not live acceptance.
        fixture,query,calls,observations=self.fixture()
        result=probe.purchase_reward(fixture,None,1,11,12,'earned',7,query,lambda p,m:self.assertTrue(p(),m))
        self.assertTrue(result['actualRewardPurchased']);self.assertEqual(90,result['paidAmount'])
        self.assertIn(('GET','/orders/confirmation?courseIds=7&couponIds=11',None,None),calls)
        creates=[c for c in calls if c[0:2]==('POST','/orders')]
        self.assertEqual(2,len(creates));self.assertEqual(creates[0],creates[1]);self.assertEqual(['11'],creates[0][2]['couponIds'])
        self.assertEqual({'tj_course','tj_promotion','tj_trade','tj_pay','tj_learning'},{db for db,_ in observations})
        self.assertTrue(any('lesson.grant' in sql for _,sql in observations));self.assertTrue(any('coupon.confirm' in sql for _,sql in observations))
        for _,sql in observations:
            if 'event_id' in sql:
                self.assertIn('CAST(o.event_id AS BINARY)=CAST(i.event_id AS BINARY)',sql)

if __name__=='__main__':unittest.main()
