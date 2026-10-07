"""Create explicit browser-only course/exam fixtures; no baseline row replacement."""
from prepare import mysql,LOCAL
import json,time,uuid
def main():
 marker=uuid.uuid4().hex[:12];course=810000000000000000+int(time.time())*10;chapter=course+1;video=course+2;exam=course+3;objective=course+4;subjective=course+5
 accounts=json.loads((LOCAL/'accounts.json').read_text());admin=accounts['admin']['id'];teacher=accounts['teacher']['id']
 prior=LOCAL/'browser-fixture.json'
 if prior.exists():
  previous=json.loads(prior.read_text())
  if not previous['course'].isdigit() or not accounts['student']['id'].isdigit():raise ValueError('Invalid fixture identity')
  mysql('DELETE FROM cart WHERE user_id='+accounts['student']['id']+' AND course_id='+previous['course'],'tj_trade')
 columns=[line.split('\t')[0] for line in mysql('SHOW COLUMNS FROM course','tj_course').splitlines()]
 substitutions={'id':str(course),'name':"'Browser course "+marker+"'",'status':'2','free':'0','price':'100','purchase_end_time':'NOW()+INTERVAL 1 YEAR','purchase_start_time':'NOW()-INTERVAL 1 DAY','valid_duration':'12','section_num':'2','media_duration':'2','creater':admin,'updater':admin,'deleted':'0','publish_times':'1'}
 values=','.join(substitutions.get(c,'`'+c+'`') for c in columns)
 mysql('INSERT INTO course('+','.join('`'+c+'`' for c in columns)+') SELECT '+values+' FROM course WHERE id=1','tj_course')
 for i,name,type,parent in [(chapter,'Browser chapter',1,0),(video,'Browser video',2,chapter),(exam,'Browser exam',3,chapter)]:
  mysql(f"INSERT INTO course_catalogue(id,name,course_id,type,parent_catalogue_id,media_duration,c_index,dep_id,creater,updater) VALUES({i},'{name}',{course},{type},{parent},2,{1 if type<3 else 2},0,{admin},{admin})",'tj_course')
 mysql(f"INSERT INTO course_teacher(id,course_id,teacher_id,is_show,c_index,dep_id,create_time,update_time,creater,updater,deleted) VALUES({course},{course},{teacher},1,1,0,NOW(),NOW(),{admin},{admin},0)",'tj_course')
 for i,name,type,answer in [(objective,'Browser objective',2,'1,2'),(subjective,'Browser subjective',5,'reference')]:
  mysql(f"INSERT INTO question(id,name,type,cate_id1,cate_id2,cate_id3,difficulty,score,creater,updater) VALUES({i},'{name}',{type},1,2,3,1,10,{admin},{admin});INSERT INTO question_detail(id,options,answer,analysis) VALUES({i},'[\"A\",\"B\",\"C\"]','{answer}','Acceptance');INSERT INTO question_biz(biz_id,question_id) VALUES({exam},{i})",'tj_exam')
 # Independent draft course + historical learning access for note tests.
 # Financial tests refund course 1; they must not remove this test's permission.
 notes_course=course+10;notes_lesson=course+11
 notes_substitutions={**substitutions,'id':str(notes_course),'name':"'Browser notes "+marker+"'",'status':'1'}
 notes_values=','.join(notes_substitutions.get(c,'`'+c+'`') for c in columns)
 mysql('INSERT INTO course('+','.join('`'+c+'`' for c in columns)+') SELECT '+notes_values+' FROM course WHERE id=1','tj_course')
 mysql(f"INSERT INTO learning_lesson(id,user_id,course_id,expire_time,status,learned_sections) VALUES({notes_lesson},{accounts['student']['id']},{notes_course},NOW()+INTERVAL 1 YEAR,0,0)",'tj_learning')
 # A current free course verifies enrollment without rewriting expired legacy courses.
 free_course=course+20
 free_substitutions={**substitutions,'id':str(free_course),'name':"'Browser free "+marker+"'",'free':'1','price':'0','section_num':'1'}
 free_values=','.join(free_substitutions.get(c,'`'+c+'`') for c in columns)
 mysql('INSERT INTO course('+','.join('`'+c+'`' for c in columns)+') SELECT '+free_values+' FROM course WHERE id=1','tj_course')
 fixture=dict(marker=marker,freeCourse=str(free_course),notesCourse=str(notes_course),notesLesson=str(notes_lesson),course=str(course),chapter=str(chapter),video=str(video),exam=str(exam),objective=str(objective),subjective=str(subjective),name='Browser course '+marker)
 (LOCAL/'browser-fixture.json').write_text(json.dumps(fixture,indent=2))
 print('Fresh isolated browser fixtures created')
if __name__=='__main__':main()
