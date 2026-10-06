"""Create only dedicated acceptance_* integration-test schemas."""
from prepare import BASE,mysql
import re
sql=(BASE/'test-schema.sql').read_text(encoding='utf8')
names=set(re.findall(r'USE `([^\`]+)`',sql))
if names!={'acceptance_common','acceptance_learning','acceptance_exam','acceptance_pay'}:raise RuntimeError('Unexpected target schemas')
if re.search(r'^INSERT\s',sql,re.M):raise RuntimeError('Schema fixture must not contain application data')
mysql(sql);print('Dedicated integration-test schemas initialized.')
