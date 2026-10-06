"""Create dedicated acceptance accounts; refuse to overwrite user-provided identities."""
import bcrypt,json,secrets
from prepare import mysql,LOCAL
path=LOCAL/'accounts.json'
if path.exists():print('Acceptance account fixture already exists')
else:
 accounts={}
 for index,(label,kind,role) in enumerate([('student',2,2),('admin',1,1),('teacher',3,3)]):
  uid=800000000000000001+index;username='acceptance_'+label;password=secrets.token_urlsafe(18)
  if mysql(f"SELECT id FROM user WHERE id={uid} OR username='{username}'",'tj_user'):raise RuntimeError('Fixture identity is already occupied')
  digest=bcrypt.hashpw(password.encode(),bcrypt.gensalt()).decode()
  mysql(f"INSERT INTO user(id,username,cell_phone,password,type,status) VALUES({uid},'{username}','1990000000{index}','{digest}',{kind},1);INSERT INTO user_detail(id,type,name,role_id) VALUES({uid},{kind},'{label} acceptance',{role})",'tj_user')
  accounts[label]={'id':str(uid),'username':username,'password':password,'role':role}
 path.write_text(json.dumps(accounts,indent=2),encoding='utf8');print('Created isolated accounts; credentials retained locally')
