/** Public v2 learning contracts. Identifiers stay decimal strings across JS boundaries. */
export interface Page<T> {list:T[];total:number}
export type SeasonStatus='LEGACY_READONLY'|'OPEN'|'SETTLING'|'BLOCKED'|'SETTLED';
export interface Season {month:string;name:string;status:SeasonStatus;freezeAt?:string;settledAt?:string;blockedReason?:string}
export interface BoardEntry {rank:number;userId:string;name:string;points:number;reachedAt:string}
export interface SeasonBoard extends Page<BoardEntry> {month:string;status:SeasonStatus;settledAt?:string;myRank:number;myPoints:number;gapToPrevious:number;rewardRules:RewardRule[]}
export interface RewardRule {id:string;month:string;rankFrom:number;rankTo:number;couponId:string;enabled:boolean;version:number}
export interface PointEntry {id:string;type:number;typeName:string;points:number;occurredAt:string;creditedAt?:string;month:string;late:boolean}
export interface Achievement {code:string;name:string;awardedAt:string;seasonMonth?:string}
export interface SignCalendar {month:string;signDays:number;todaySigned:boolean;daysInMonth:number;signRecords:number[];rewards:{days:number;points:number}[]}
export interface ReviewItem {attempt_id:string;question_id:string;course_id:string;type:number;name:string;options:string|string[];answer:string;analysis?:string;response?:string;score:number;max_score:number;feedback?:string;mastered:boolean;version:number|string;last_reviewed_at?:string}
export interface LearningStudent {userId:string;name:string;learnedSections:number;sections:number;progressPercent:number;status:number;latestLearnTime?:string;expireTime?:string}
export interface TeachingOverview extends Page<LearningStudent> {courseId:string;courseName:string;summary:{students:number;completedStudents:number;completionRate:number;unansweredQuestions:number;unacceptedQuestions:number}}
export interface LearningReminders {pendingExams:number|null;waitingForGradingExams:number|null;examReminderError:string|null;unreadReplies:number|null;replyReminderError:string|null}

/** OPEN is a backend lifecycle state; its user label also depends on the Shanghai month. */
export function seasonLabel(status:unknown,month:unknown,now=new Date()):string {
 const current=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit'}).format(now).replace('-','');
 if(status==='OPEN' && /^[0-9]{6}$/.test(String(month)) && String(month)>current)return '未开始';
 return ({LEGACY_READONLY:'历史赛季',OPEN:'进行中',SETTLING:'结算中',BLOCKED:'等待核对',SETTLED:'已结算'} as Record<string,string>)[String(status)]||'状态待确认';
}
