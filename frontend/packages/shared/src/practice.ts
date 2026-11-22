export interface PracticeAssignment {
  courseId: string;
  title: string;
  instructions: string;
  publishedAt?: string;
  version: number | null;
}

export interface PracticeVersion {
  version: number;
  title: string;
  instructions: string;
  changeReason: string;
  effectiveAt: string;
}

export interface PracticeAssignmentView {
  assignment: PracticeAssignment | null;
  versions: PracticeVersion[];
}

export type PracticeStatus = 'SUBMITTED' | 'NEEDS_REVISION' | 'PASSED';

export interface PracticeSubmission {
  id: string;
  userId: string;
  revision: number;
  assignmentVersion: number;
  description: string;
  workUrl?: string;
  status: PracticeStatus;
  feedback?: string;
  submittedAt: string;
  reviewedAt?: string;
}

export const practiceStatus: Record<PracticeStatus, string> = {
  SUBMITTED: '待反馈',
  NEEDS_REVISION: '需要修改',
  PASSED: '已通过',
};
