"""Create the isolated index; rebuild legacy sales only during a quiescent acceptance window."""
import requests,json,argparse
from prepare import mysql,LOCAL
ES='http://127.0.0.1:23920'
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--schema-only',action='store_true');args=parser.parse_args()
 fields={k:{'type':'long'} for k in ['categoryIdLv1','categoryIdLv2','categoryIdLv3','teacher','sections','sold','price','score','salesVersion','metadataVersion']}
 fields.update(available={'type':'boolean'},id={'type':'keyword'},name={'type':'text','analyzer':'standard'},free={'type':'boolean'},type={'type':'integer'},publishTime={'type':'date','format':'strict_date_optional_time||yyyy-MM-dd HH:mm:ss'},coverUrl={'type':'keyword'})
 response=requests.put(ES+'/course',json={'settings':{'number_of_shards':1,'number_of_replicas':0},'mappings':{'properties':fields}},timeout=10)
 if response.status_code!=200 and 'resource_already_exists_exception' not in response.text:response.raise_for_status()
 if args.schema_only:
  print('Isolated search mapping initialized.');return
 # Historical detail provenance, keeping refund tombstones. Rebuild is an explicit maintenance operation.
 rows=mysql('SELECT id,course_id,IF(status IN(2,4,5,6) AND COALESCE(refund_status,0)<>5,1,0) FROM order_detail WHERE status IN(2,4,5,6,7)','tj_trade')
 if rows:
  statements=[]
  for line in rows.splitlines():
   detail,course,active=line.split('\t');assert all(x.isdigit() for x in [detail,course,active])
   statements.append(f'INSERT INTO course_sale_detail(order_detail_id,course_id,active) VALUES({detail},{course},{active}) ON DUPLICATE KEY UPDATE active=LEAST(active,VALUES(active));')
  mysql(''.join(statements),'tj_search')
  mysql('INSERT INTO course_sales_projection(course_id,sold,version,processed_version) SELECT course_id,SUM(active),1,0 FROM course_sale_detail GROUP BY course_id ON DUPLICATE KEY UPDATE sold=VALUES(sold),version=version+1','tj_search')
 print('Isolated search mapping and quiescent legacy sales rebuild complete.')
if __name__=='__main__':main()
