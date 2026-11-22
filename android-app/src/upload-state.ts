export type UploadJob = {
  id: string;
  scope: string;
  folder: string;
  name: string;
  phase:
    "preparing" | "queued" | "uploading" | "complete" | "error" | "cancelled";
  bytes: number;
  total: number;
  dismissed: boolean;
  stopping?: boolean;
  error?: string;
};
export const uploadPending = (job: UploadJob) =>
  job.phase === "preparing" ||
  job.phase === "queued" ||
  job.phase === "uploading";

export function uploadSummary(jobs: UploadJob[]) {
  const visible = jobs.filter((job) => !job.dismissed);
  const active = visible.filter(uploadPending);
  const complete = visible.filter((job) => job.phase === "complete").length;
  const failed = visible.filter((job) => job.phase === "error").length;
  const cancelled = visible.filter((job) => job.phase === "cancelled").length;
  const total = visible.reduce((sum, job) => sum + Math.max(0, job.total), 0);
  const bytes = visible.reduce(
    (sum, job) =>
      sum +
      (job.phase === "complete"
        ? job.total
        : Math.min(Math.max(0, job.bytes), Math.max(0, job.total))),
    0,
  );
  return {
    visible,
    active: active.length,
    complete,
    failed,
    cancelled,
    total,
    bytes,
    current: active.find((job) => job.phase === "uploading") || active[0],
    percent:
      total > 0
        ? Math.min(active.length ? 99 : 100, Math.round((bytes * 100) / total))
        : 0,
  };
}

export function completedUploadStamp(jobs: UploadJob[], folder: string) {
  return jobs
    .filter((job) => job.folder === folder && job.phase === "complete")
    .map((job) => job.id)
    .join("|");
}
