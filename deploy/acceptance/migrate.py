from pathlib import Path
import hashlib,json
from prepare import mysql,BASE,DATABASES,LOCAL
PREFLIGHT={
 'tj_auth':[("role_privilege","role_id,privilege_id")],
 'tj_media':[("media","file_id")],
 'tj_trade':[("cart","user_id,course_id")],
 'tj_learning':[("learning_record","lesson_id,section_id")],
 'tj_remark':[("liked_record","user_id,biz_type,biz_id")],
}
def main():
    if mysql("SELECT order_detail_id,COUNT(*) FROM refund_apply WHERE status IN(1,3) GROUP BY order_detail_id HAVING COUNT(*)>1 LIMIT 20",'tj_trade'):
        raise RuntimeError('Active refund duplicates require manual reconciliation before adding constraints')
    for db,checks in PREFLIGHT.items():
        for table,cols in checks:
            duplicates=mysql(f"SELECT {cols},COUNT(*) FROM {table} GROUP BY {cols} HAVING COUNT(*)>1 LIMIT 20",db)
            if duplicates: raise RuntimeError(f"Duplicate records in {db}.{table}; migrate only after manual reconciliation")
    results=[]
    for db in DATABASES:
        mysql("CREATE TABLE IF NOT EXISTS schema_migration(version VARCHAR(128) PRIMARY KEY,checksum CHAR(64) NOT NULL,applied_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3))",db)
        scripts=list((BASE/'migrations'/'common').glob('V*.sql'))+list((BASE/'migrations'/db).glob('V*.sql'))
        for script in sorted(scripts,key=lambda p:p.name):
            data=script.read_bytes();checksum=hashlib.sha256(data).hexdigest()
            name=script.parent.name+'/'+script.name
            existing=mysql("SELECT checksum FROM schema_migration WHERE version='"+name+"'",db)
            if existing:
                if existing!=checksum: raise RuntimeError('Migration checksum changed: '+db+'/'+name)
                continue
            # MySQL DDL is not transactional. Failed migration stops for inspection; no automatic destructive repair.
            mysql(data.decode('utf8'),db)
            mysql("INSERT INTO schema_migration(version,checksum) VALUES('"+name+"','"+checksum+"')",db)
            results.append(db+'/'+name)
    LOCAL.mkdir(exist_ok=True);(LOCAL/'migrations.json').write_text(json.dumps(results,indent=2))
    print(f"Applied {len(results)} isolated migrations.")
if __name__=='__main__':main()
