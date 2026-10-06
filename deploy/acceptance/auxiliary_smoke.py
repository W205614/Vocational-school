"""Real gateway + private persistence: media bytes, durable notices and explicit SMS simulation."""
import uuid,json,time,requests,concurrent.futures
from api_smoke import call,login,wait,BASE,accounts
from financial_smoke import eventually
from prepare import mysql,LOCAL
def main():
 admin=login('admin');student=login('student');run=uuid.uuid4().hex[:12]
 call(student,'GET','/admin/dashboard',expected=403)
 dashboard=call(admin,'GET','/admin/dashboard');assert isinstance(dashboard,dict)
 template=call(admin,'POST','/admin/message/notice-templates',dict(name='Acceptance '+run,code='ACC_'+run,type=0,status=1,title='Durable notification '+run,content='Private test notice',isSmsTemplate=False))
 body=dict(name='Notice '+run,templateId=template,partial=True,userIds=[accounts['student']['id']],maxTimes=0)
 key=uuid.uuid4().hex[:12]
 op=call(admin,'POST','/admin/message/notice-tasks',body,key,202)
 assert call(admin,'POST','/admin/message/notice-tasks',body,key,202)['operationId']==op['operationId']
 result=wait(admin,'message',op);assert result['status']=='SUCCEEDED',result
 task=str(result['result']['taskId'])
 assert call(admin,'GET','/admin/message/notice-tasks/'+task)['userIds']==[accounts['student']['id']]
 eventually(lambda:mysql('SELECT finished+0 FROM notice_task WHERE id='+task,'tj_message')=='1','notice task')
 title='Durable notification '+run
 before=mysql("SELECT COUNT(*) FROM user_inbox WHERE title='"+title+"' AND user_id="+accounts['student']['id'],'tj_message')
 assert before=='1'
 for _ in range(3):call(student,'GET','/services/message/inboxes?pageNo=1&pageSize=100')
 assert mysql("SELECT COUNT(*) FROM user_inbox WHERE title='"+title+"' AND user_id="+accounts['student']['id'],'tj_message')=='1'
 inbox=mysql("SELECT id FROM user_inbox WHERE title='"+title+"' AND user_id="+accounts['student']['id'],'tj_message')
 call(student,'PUT','/services/message/inboxes/'+inbox+'/read');call(student,'PUT','/services/message/inboxes/'+inbox+'/read')
 bad=wait(admin,'message',call(admin,'POST','/admin/message/notice-tasks',dict(body,userIds=[]),uuid.uuid4().hex,202));assert bad['status']=='FAILED'
 sms=dict(templateCode='ACCEPTANCE_ONLY',phones=['13800000000'],templateParams={'code':'123456'})
 key=uuid.uuid4().hex;op=call(admin,'POST','/admin/message/sms/message',sms,key,202)
 assert call(admin,'POST','/admin/message/sms/message',sms,key,202)['operationId']==op['operationId']
 result=wait(admin,'message',op);assert result['status']=='SUCCEEDED',result
 delivery=result['result']['deliveryId']
 eventually(lambda:mysql("SELECT status FROM sms_delivery_task WHERE id='"+delivery+"'",'tj_message')=='DONE','simulated sms')
 assert mysql("SELECT COUNT(*) FROM simulated_sms WHERE task_id='"+delivery+"'",'tj_message')=='1'
 # Actual byte persistence and signed HTTP range, independent of transcoding claims.
 content=b'acceptance-video-byte-fixture-'+run.encode()
 key=uuid.uuid4().hex[:12]
 response=admin.post(BASE+'/admin/media-upload',files={'file':('fixture.webm',content,'video/webm')},data={'duration':'60'},headers={'Idempotency-Key':key},timeout=30)
 assert response.status_code==202,response.text[:500]
 op=response.json()['data'];result=wait(admin,'media',op);assert result['status']=='SUCCEEDED',result
 media=result['result'];media['fileId']=mysql('SELECT file_id FROM media WHERE id='+str(media['id']),'tj_media');preview=call(admin,'GET','/admin/media/medias/signature/preview?mediaId='+str(media['id']))
 url='http://127.0.0.1:23310'+preview['playUrl']
 r=requests.get(url,timeout=10);assert r.status_code==200 and r.content==content,(r.status_code,r.text[:200])
 r=requests.get(url,headers={'Range':'bytes=0-9'},timeout=10);assert r.status_code==206 and r.content==content[:10],(r.status_code,r.text[:200])
 invalid=url.replace('signature=','signature=invalid');assert requests.get(invalid,timeout=10).status_code==403
 deleted=wait(admin,'media',call(admin,'DELETE','/admin/media/medias/'+str(media['id']),key=uuid.uuid4().hex,expected=202));assert deleted['status']=='SUCCEEDED',deleted
 eventually(lambda:not (LOCAL/'objects'/media['fileId']).exists(),'storage deletion')
 (LOCAL/'auxiliary-smoke.json').write_text(json.dumps(dict(status='PASSED',checks=['dashboard role boundary','durable targeted notification and duplicate command','empty targets fail without broadcast','inbox refresh uniqueness and owned read','durable explicit SMS simulation once','persistent media bytes','signed URL and HTTP range','invalid signature denial','persistent cleanup']),indent=2))
 print('Auxiliary real gateway smoke PASSED',flush=True)
if __name__=='__main__':main()
