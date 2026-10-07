"""Generate up to 200 explicit load-only identities in this independent deployment."""
import json,secrets
from setup import LOCAL,mysql
accounts=json.loads((LOCAL/'accounts.json').read_text(encoding='utf8'));fixture=json.loads((LOCAL/'browser-fixture.json').read_text(encoding='utf8'));path=LOCAL/'load-accounts.json'
if path.exists():print('Existing load-only identities retained');raise SystemExit()
import bcrypt
password=secrets.token_urlsafe(24);hashed=bcrypt.hashpw(password.encode(),bcrypt.gensalt()).decode();base=850000000000000000;source=accounts['student']['id']
columns=lambda table:[line.split('\t')[0] for line in mysql('SHOW COLUMNS FROM tj_user.'+table).splitlines()]
users=[];statements=[];schema={table:columns(table) for table in ['user','user_detail']}
for n in range(200):
 uid=base+n;username='compact-load-'+str(n);values={'id':str(uid),'username':"'"+username+"'",'password':"'"+hashed+"'",'cell_phone':"'189"+str(n).zfill(8)+"'",'auth_version':'0','status':'1'}
 for table in ['user','user_detail']:
  cols=schema[table]
  expression=','.join(values.get(c,'`'+c+'`') for c in cols)
  statements.append('INSERT INTO tj_user.'+table+'('+','.join('`'+c+'`' for c in cols)+') SELECT '+expression+' FROM tj_user.'+table+' WHERE id='+source+';')
 statements.append('INSERT INTO tj_learning.learning_lesson(id,user_id,course_id,expire_time,status,learned_sections) VALUES('+str(base+1000+n)+','+str(uid)+','+fixture['notesCourse']+',NOW()+INTERVAL 1 YEAR,0,0);')
 users.append(dict(id=str(uid),username=username,password=password))
mysql('START TRANSACTION;'+''.join(statements)+'COMMIT;')
path.write_text(json.dumps(users,indent=2),encoding='utf8');print('Created 200 independent load identities; credentials remain private')
