"""Read-only checks of completed learning, exam results and private notes after restart."""
from pathlib import Path
import json
from runtime import configure
p=configure();f=json.loads((p.LOCAL/'browser-fixture.json').read_text(encoding='utf8'));u=json.loads((p.LOCAL/'accounts.json').read_text(encoding='utf8'))['student']['id']
def main():
 assert all(f[k].isdigit() for k in ['course','video','exam'])
 assert f['marker'].isalnum() and u.isdigit()
 record_count=p.mysql('SELECT COUNT(*) FROM learning_record r JOIN learning_lesson l ON l.id=r.lesson_id WHERE l.user_id='+u+' AND l.course_id='+f['course']+' AND r.finished=1 AND r.section_id IN('+f['video']+','+f['exam']+')','tj_learning')
 assert record_count=='2','Completed section history missing'
 assert p.mysql('SELECT learned_sections FROM learning_lesson WHERE user_id='+u+' AND course_id='+f['course'],'tj_learning')=='2','Lesson completed count changed'
 assert p.mysql('SELECT COUNT(*) FROM exam_attempt a JOIN exam_paper p ON p.id=a.paper_id WHERE a.user_id='+u+' AND p.section_id='+f['exam']+' AND a.passed=1 AND a.score=20','tj_exam')=='1','Exam result changed'
 assert p.mysql("SELECT COUNT(*) FROM course_note WHERE user_id="+u+" AND course_id="+f['course']+" AND deleted=0 AND content='Video note "+f['marker']+"'",'tj_learning')=='1','Private note missing'
 (p.LOCAL/'history-recovery.json').write_text(json.dumps({'status':'PASSED','completedSections':2,'lessonCompletedCount':2,'passedExamScore':20,'privateNoteRetained':True}),encoding='utf8')
 print('Completed sections, lesson count, exam result and private note retained',flush=True)
if __name__=='__main__':main()
