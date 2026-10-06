"""Export schema only, never application rows or credentials."""
from prepare import COMPOSE,BASE,LOCAL
import subprocess,re
TARGETS=['acceptance_common','acceptance_learning','acceptance_exam','acceptance_pay']
result=subprocess.run(COMPOSE+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --no-data --skip-comments --skip-add-drop-table --no-tablespaces --set-gtid-purged=OFF --databases '+' '.join(TARGETS)],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
if result.returncode:
 (LOCAL/'schema-export-errors.log').write_bytes(result.stderr);raise RuntimeError('Schema export failed; private diagnostics retained')
schema=re.sub(r' AUTO_INCREMENT=\d+','',result.stdout.decode('utf8'))
if re.search(r'^INSERT\s',schema,re.M):raise RuntimeError('Refusing schema export with data')
(BASE/'test-schema.sql').write_text(schema,encoding='utf8')
print('Four schema-only test databases exported, with no application data.')
