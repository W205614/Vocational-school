export type ClassState = 'DRAFT' | 'OPEN' | 'CLOSED' | 'ARCHIVED' | 'CANCELED';
export type MemberState = 'HELD' | 'ACTIVE' | 'WITHDRAWN' | 'TRANSFERRED' | 'REFUNDED' | 'RELEASED' | 'COMPLETED';
export type ActivityKind = 'LESSON' | 'ASSIGNMENT' | 'EXAM' | 'ANNOUNCEMENT';
export type Duty = 'TEACHER' | 'ASSISTANT';

export interface Classroom {
  id: string;
  courseId: string;
  name: string;
  state: ClassState;
  registrationOpens: string;
  registrationCloses: string;
  startsAt: string;
  endsAt: string;
  capacity: number;
  passPercent: number;
  remaining: number;
}

export interface Membership {
  id: string;
  classId: string;
  userId: string;
  orderId?: string;
  state: MemberState;
  joinedAt: string;
}

export interface Activity {
  id: string;
  kind: ActivityKind;
  sectionId?: string;
  paperId?: string;
  title: string;
  scheduledAt: string;
  closesAt?: string;
  weight: number;
  maxAttempts?: number;
  currentVersion: number;
  instructions: string;
}

export interface Version {
  version: number;
  title: string;
  instructions: string;
  reason?: string;
  changedAt: string;
}

export interface Submission {
  id: string;
  memberId: string;
  userId: string;
  version: number;
  revision: number;
  description: string;
  workUrl?: string;
  state: 'SUBMITTED' | 'NEEDS_REVISION' | 'REVIEWED';
  score?: number;
  feedback?: string;
}

export interface Discussion {
  id: string;
  parentId?: string;
  sectionId?: string;
  title?: string;
  body: string;
  staffReply: boolean;
  postedAt: string;
}

export interface GradeItem {
  activityId: string;
  title: string;
  kind: ActivityKind;
  weight: number;
  state: 'NOT_SUBMITTED' | 'NEEDS_REVISION' | 'IN_PROGRESS' | 'PENDING_REVIEW' | 'READY' | 'PUBLISHED';
  score?: number;
  feedback?: string;
}

export interface GradeReport {
  memberId: string;
  userId: string;
  name?: string;
  membershipState: MemberState;
  items: GradeItem[];
  result?: { totalPercent: number; passed: boolean; finalizedAt: string };
}

export const activityName: Record<ActivityKind, string> = {
  LESSON: '课节', ASSIGNMENT: '作业', EXAM: '考试', ANNOUNCEMENT: '公告',
};
export const classStateName: Record<ClassState, string> = {
  DRAFT: '草稿', OPEN: '已发布', CLOSED: '已停止报名', ARCHIVED: '已结班', CANCELED: '已取消',
};
export const memberStateName: Record<MemberState, string> = {
  HELD: '等待订单确认', ACTIVE: '在班', WITHDRAWN: '已退班', TRANSFERRED: '已转班',
  REFUNDED: '已退款', RELEASED: '占座已释放', COMPLETED: '已结班',
};
export const gradeStateName: Record<GradeItem['state'], string> = {
  NOT_SUBMITTED: '未提交', NEEDS_REVISION: '需修改', IN_PROGRESS: '等待成绩发布',
  PENDING_REVIEW: '待批改', READY: '待发布', PUBLISHED: '已发布',
};

export function classTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—';
}
