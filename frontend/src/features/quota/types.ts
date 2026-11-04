/** Loại tác vụ tốn lượt, giống QuotaTask ở máy chủ; loại mới chưa có ở đây vẫn hiển thị bằng mã. */
export type QuotaTask =
  | 'ASSISTANT_QUESTION'
  | 'EXPLAIN_PASSAGE'
  | 'IMAGE_TEXT_RECOGNITION'
  | 'DOCUMENT_SUMMARY'
  | 'FLASHCARD_GENERATION'
  | 'QUIZ_GENERATION'
  | 'STUDY_GUIDE'

/** Tác vụ nhẹ (một câu trả lời ngắn) hay sinh nội dung dài. */
export type QuotaTaskWeight = 'LIGHT' | 'LONG'

export interface TaskCost {
  task: QuotaTask | (string & {})
  weight: QuotaTaskWeight
  units: number
}

/** Đồng hồ lượt của người học (NCL-04-CN-003). */
export interface QuotaStatus {
  ai: {
    dailyUnits: number
    usedUnits: number
    /** Lượt đang giữ chỗ cho tác vụ chưa xong; được hoàn lại nếu tác vụ lỗi. */
    reservedUnits: number
    remainingUnits: number
    studyDay: string
    timeZone: string
    /** Nửa đêm kế tiếp theo múi giờ của người học, lúc lượt hồi đủ. */
    resetsAt: string
  }
  /** {@code maxFileBytes}: dung lượng tối đa một tệp tải lên do quản trị viên đặt. */
  storage: { limitBytes: number; usedBytes: number; remainingBytes: number; maxFileBytes?: number }
  costs: TaskCost[]
}

export interface QuotaActor {
  id: string
  displayName?: string | null
}

/** Trang hạn mức của quản trị viên. */
export interface AdminQuota {
  policy: {
    dailyAiUnits: number
    storageLimitBytes: number
    /** Dung lượng tối đa một tệp tải lên (NCL-05-CN-002). */
    maxUploadFileBytes: number
    updatedAt: string
    /** Trống khi vẫn là giá trị mặc định lúc cài đặt. */
    updatedBy?: QuotaActor | null
  }
  costs: (TaskCost & { defaultUnits: number; updatedAt: string; updatedBy?: QuotaActor | null })[]
}

export interface QuotaPolicyInput {
  dailyAiUnits: number
  storageLimitMb: number
  maxUploadFileMb: number
}
