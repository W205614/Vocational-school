"""Read all primary administration resources through the acceptance gateway."""
from api_smoke import login,call
from prepare import LOCAL
import json
def main():
 admin=login('admin');checks={};failures=[]
 for name,path in {
 'courses':'/admin/course/courses/page','media':'/admin/media/medias','questions':'/admin/exam/questions/page',
 'students':'/admin/user/students/page','staff':'/admin/user/staffs/page','roles':'/admin/auth/roles/list',
 'privileges':'/admin/auth/privileges','coupons':'/admin/promotion/coupons/page',
 'orders':'/admin/trade/order-details/page','refunds':'/admin/trade/refund-apply/page',
 'templates':'/admin/message/notice-templates','notices':'/admin/message/notice-tasks'}.items():
  response=admin.get('http://127.0.0.1:23310/api/v2'+path,params=dict(pageNo=1,pageSize=20),timeout=20)
  try:body=response.json();ok=response.status_code==200 and body.get('code')==200
  except ValueError:ok=False;body={}
  checks[name]=dict(http=response.status_code,code=body.get('code'),requestId=body.get('requestId'))
  if not ok:failures.append(name)
 (LOCAL/'page-api.json').write_text(json.dumps(dict(status='FAILED' if failures else 'PASSED',checks=checks),indent=2))
 if failures:raise AssertionError('Administration resource errors: '+','.join(failures))
 print('12 administration resource APIs PASSED',flush=True)
if __name__=='__main__':main()
