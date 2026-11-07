"""Fail before Docker build when a compact host embeds executable instead of plain module jars."""

import io
import zipfile
from pathlib import Path

CLASSES = {
    'auth': 'com/zhixuetang/auth/AuthApplication.class',
    'user': 'com/zhixuetang/user/UserApplication.class',
    'trade': 'com/zhixuetang/trade/TradeApplication.class',
    'pay': 'com/zhixuetang/pay/PayApplication.class',
    'promotion': 'com/zhixuetang/promotion/PromotionApplication.class',
    'course': 'com/zhixuetang/course/CourseApplication.class',
    'learning': 'com/zhixuetang/learning/LearningApplication.class',
    'exam': 'com/zhixuetang/exam/ExamApplication.class',
    'remark': 'com/zhixuetang/remark/RemarkApplication.class',
    'media': 'com/zhixuetang/media/MediaApplication.class',
    'search': 'com/zhixuetang/search/SearchApplication.class',
    'message': 'com/zhixuetang/message/MessageApplication.class',
    'data': 'com/zhixuetang/data/DataCenterApplication.class',
}


def verify(jar, aliases):
    found = set()
    with zipfile.ZipFile(jar) as host:
        for name in host.namelist():
            if not name.startswith('BOOT-INF/lib/zxt-') or not name.endswith('.jar'):
                continue
            with zipfile.ZipFile(io.BytesIO(host.read(name))) as module:
                entries = set(module.namelist())
                for alias in aliases:
                    if CLASSES[alias] in entries:
                        found.add(alias)
                    if 'BOOT-INF/classes/' + CLASSES[alias] in entries:
                        raise ValueError(
                            'Executable dependency in compact host: ' + alias
                        )
    missing = set(aliases) - found
    if missing:
        raise ValueError('Missing flat module classes: ' + ','.join(sorted(missing)))


if __name__ == '__main__':
    from setup import ROOT, GROUPS

    for group, aliases in GROUPS.items():
        verify(
            ROOT / 'zxt-compact' / group / 'target' / ('zxt-' + group + '-app.jar'),
            aliases,
        )
    print('Compact dependency packaging verified')
