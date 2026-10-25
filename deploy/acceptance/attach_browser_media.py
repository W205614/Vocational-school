import argparse,json
from prepare import mysql,LOCAL
parser=argparse.ArgumentParser();parser.add_argument('media',type=int);args=parser.parse_args()
fixture=json.loads((LOCAL/'browser-fixture.json').read_text());video=fixture['video'];course=fixture['course']
if mysql('SELECT COUNT(*) FROM media WHERE id='+str(args.media)+' AND deleted=0','tj_media')!='1':raise ValueError('Media fixture unavailable')
duration=mysql('SELECT CEIL(duration) FROM media WHERE id='+str(args.media),'tj_media')
if not duration.isdigit() or int(duration)<1:raise ValueError('Uploaded media duration unavailable')
mysql('UPDATE course_catalogue SET media_id='+str(args.media)+",video_name='Browser fixture.webm',media_duration="+duration+' WHERE id='+video+' AND course_id='+course,'tj_course')
print('Browser-only video association prepared')
