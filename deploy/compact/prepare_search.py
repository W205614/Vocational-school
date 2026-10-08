import requests
from setup import OFFSET
fields={k:{'type':'long'} for k in ['categoryIdLv1','categoryIdLv2','categoryIdLv3','teacher','sections','sold','price','score','salesVersion','metadataVersion']}
fields.update(available={'type':'boolean'},id={'type':'keyword'},name={'type':'text','analyzer':'standard'},free={'type':'boolean'},type={'type':'integer'},publishTime={'type':'date','format':'strict_date_optional_time||yyyy-MM-dd HH:mm:ss'},coverUrl={'type':'keyword'})
fields['updateTime']={'type':'date','format':'strict_date_optional_time||yyyy-MM-dd HH:mm:ss'}
r=requests.put('http://127.0.0.1:'+str(24920+OFFSET)+'/course',json={'settings':{'number_of_shards':1,'number_of_replicas':0},'mappings':{'properties':fields}},timeout=10)
if not r.ok and 'resource_already_exists_exception' not in r.text:r.raise_for_status()
print('Compact search mapping initialized')
