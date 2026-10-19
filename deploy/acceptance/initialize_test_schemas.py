"""Create only dedicated acceptance_* integration-test schemas."""
from prepare import BASE,mysql
import re
sql=(BASE/'test-schema.sql').read_text(encoding='utf8')
names=set(re.findall(r'USE `([^\`]+)`',sql))
if names!={'acceptance_common','acceptance_learning','acceptance_exam','acceptance_pay'}:raise RuntimeError('Unexpected target schemas')
if re.search(r'^INSERT\s',sql,re.M):raise RuntimeError('Schema fixture must not contain application data')
sql=re.sub(r'CREATE TABLE `', 'CREATE TABLE IF NOT EXISTS `', sql)
mysql(sql)
# New features evolve existing dedicated schemas through checksummed migrations.
from migrate import apply_migrations
bootstrap=(BASE.parents[1]/'deploy/compact/schema.sql').read_text(encoding='utf8')
for table in ('interaction_question','interaction_reply'):
 definition=re.search(r'CREATE TABLE `'+table+r'` \(.*?\) ENGINE=.*?;',bootstrap,re.S)
 if definition is None:raise RuntimeError('Missing test prerequisite '+table)
 mysql(definition.group().replace('CREATE TABLE','CREATE TABLE IF NOT EXISTS',1),'acceptance_learning')
apply_migrations('acceptance_learning',sorted(p for p in (BASE/'migrations/tj_learning').glob('V*.sql') if int(p.name[1:4])>=8))
apply_migrations('acceptance_exam',sorted(p for p in (BASE/'migrations/tj_exam').glob('V*.sql') if int(p.name[1:4])>=4))
print('Dedicated integration-test schemas initialized.')
