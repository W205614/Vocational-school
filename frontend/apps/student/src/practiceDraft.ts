export interface PracticeAttempt {
  description: string;
  workUrl: string;
  assignmentVersion: number;
  submissionKey: string;
  afterRevision: number;
}

export interface PracticeDraft {
  description: string;
  workUrl: string;
  reviewedVersion: number;
  attempt?: PracticeAttempt;
}

export function draftMatchesAttempt(draft: PracticeDraft, attempt: PracticeAttempt) {
  return draft.reviewedVersion === attempt.assignmentVersion &&
    draft.description.trim() === attempt.description && draft.workUrl.trim() === attempt.workUrl;
}

export function readPracticeDraft(key: string): PracticeDraft | undefined {
  const value = localStorage.getItem(key);
  if (!value) return;
  let draft: PracticeDraft | null;
  try {
    draft = JSON.parse(value);
  } catch {
    return;
  }
  if (!draft || typeof draft.description !== 'string' || typeof draft.workUrl !== 'string' ||
    !Number.isInteger(draft.reviewedVersion) || draft.reviewedVersion < 1) return;
  const attempt = draft.attempt;
  if (attempt && (typeof attempt.description !== 'string' || typeof attempt.workUrl !== 'string' ||
    !Number.isInteger(attempt.assignmentVersion) || attempt.assignmentVersion < 1 ||
    !Number.isInteger(attempt.afterRevision) || attempt.afterRevision < 0 ||
    typeof attempt.submissionKey !== 'string' ||
    !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(attempt.submissionKey))) {
    delete draft.attempt;
  }
  return draft;
}

export function storePracticeDraft(key: string, draft: PracticeDraft) {
  if (draft.description || draft.workUrl) localStorage.setItem(key, JSON.stringify(draft));
  else localStorage.removeItem(key);
}

export function clearConfirmedPracticeDraft(key: string, attempt: PracticeAttempt) {
  const saved = readPracticeDraft(key);
  if (saved?.attempt?.submissionKey === attempt.submissionKey && draftMatchesAttempt(saved, attempt))
    localStorage.removeItem(key);
}
