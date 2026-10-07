"""Fail before Docker build when a compact host embeds executable instead of plain module jars."""
import io
import zipfile
from pathlib import Path

CLASSES = {'auth':'com/tianji/auth/AuthApplication.class','user':'com/tianji/user/UserApplication.class',
           'trade':'com/tianji/trade/TradeApplication.class','pay':'com/tianji/pay/PayApplication.class',
           'promotion':'com/tianji/promotion/PromotionApplication.class','course':'com/tianji/course/CourseApplication.class',
           'learning':'com/tianji/learning/LearningApplication.class','exam':'com/tianji/exam/ExamApplication.class',
           'remark':'com/tianji/remark/RemarkApplication.class','media':'com/tianji/media/MediaApplication.class',
           'search':'com/tianji/search/SearchApplication.class','message':'com/tianji/message/MessageApplication.class',
           'data':'com/tianji/data/DataCenterApplication.class'}

def verify(jar, aliases):
    found = set()
    with zipfile.ZipFile(jar) as host:
        for name in host.namelist():
            if not name.startswith('BOOT-INF/lib/tj-') or not name.endswith('.jar'): continue
            with zipfile.ZipFile(io.BytesIO(host.read(name))) as module:
                entries = set(module.namelist())
                for alias in aliases:
                    if CLASSES[alias] in entries: found.add(alias)
                    if 'BOOT-INF/classes/' + CLASSES[alias] in entries:
                        raise ValueError('Executable dependency in compact host: ' + alias)
    missing = set(aliases) - found
    if missing: raise ValueError('Missing flat module classes: ' + ','.join(sorted(missing)))

if __name__ == '__main__':
    from setup import ROOT, GROUPS
    for group, aliases in GROUPS.items(): verify(ROOT / 'tj-compact' / group / 'target' / ('tj-' + group + '-app.jar'), aliases)
    print('Compact dependency packaging verified')
