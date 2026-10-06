"""Explicit quiescent rebuild of current metadata. Invoke before enabling the new search alias."""
import requests,time
from api_smoke import login,call
from prepare import mysql
from financial_smoke import eventually
def main():
 admin=login('admin');ids=mysql('SELECT id FROM course WHERE status IN(2,4) AND deleted=0','tj_course').splitlines()
 for start in range(0,len(ids),100):call(admin,'POST','/admin/search/courses/up?courseIds='+','.join(ids[start:start+100]))
 for identity in ids:
  eventually(lambda:mysql('SELECT version=processed_version FROM course_metadata_projection WHERE course_id='+identity,'tj_search')=='1','metadata '+identity,90)
  row=mysql('SELECT status,version FROM course_sales_projection WHERE course_id='+identity,'tj_search')
  if row and row.split('\t')[0]=='DEAD':call(admin,'POST','/admin/search-projections/'+identity+'/replay?version='+row.split('\t')[1]+'&kind=sales')
 if ids:
  requests.post('http://127.0.0.1:23920/course/_refresh',timeout=10).raise_for_status()
 print('Authoritative metadata rebuild completed for '+str(len(ids))+' published/finished courses.')
if __name__=='__main__':main()
