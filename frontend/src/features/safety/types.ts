/** Nhóm nội dung không an toàn mà bộ lọc kiểm (NCL-04-CN-004). */
export type SafetyCategory = 'VIOLENCE' | 'HATE' | 'SEXUAL' | 'HARMFUL_INSTRUCTIONS' | 'FRAUD' | 'SELF_HARM'

export type SafetyLevel = 'STRICT' | 'STANDARD' | 'LENIENT'

export interface SupportResource {
  name: string
  /** Số gọi được; trống khi là người ở quanh người học. */
  contact?: string | null
  note: string
}

/** Câu trả lời ân cần viết sẵn cho câu hỏi có dấu hiệu tự gây hại. */
export interface SupportReply {
  language: string
  message: string
  resources: SupportResource[]
}

/**
 * Bộ lọc vừa can thiệp: chặn câu hỏi (INPUT), thay nội dung sinh ra (OUTPUT) hoặc trả lời ân cần (SUPPORT). Máy chủ
 * gửi trong sự kiện "safety" của luồng sinh nội dung.
 */
export interface SafetyOutcome {
  status: 'BLOCKED' | 'SUPPORT'
  stage?: 'INPUT' | 'OUTPUT' | null
  category?: SafetyCategory | null
  support?: SupportReply | null
}

export type SafetyEventKind = 'INPUT_BLOCKED' | 'OUTPUT_BLOCKED' | 'SELF_HARM_SUPPORT' | 'INJECTION_FLAGGED'

export type InjectionSignal = 'IGNORE_INSTRUCTIONS' | 'REVEAL_SYSTEM_PROMPT' | 'ROLE_OVERRIDE' | 'FAKE_DELIMITER'

/** Trang an toàn nội dung của quản trị viên. Sự kiện chỉ có loại vi phạm và mã đối tượng, không có nội dung. */
export interface ContentSafetyOverview {
  levels: {
    category: SafetyCategory
    level: SafetyLevel
    defaultLevel: SafetyLevel
    updatedAt?: string | null
    updatedBy?: { id: string; displayName?: string | null } | null
  }[]
  events: {
    id: string
    occurredAt: string
    kind: SafetyEventKind
    category?: SafetyCategory | null
    signal?: InjectionSignal | null
    feature: string
    objectType?: string | null
    objectId?: string | null
  }[]
}

export interface SafetyTrialDone {
  status: 'ANSWERED' | 'BLOCKED' | 'SUPPORT'
  flaggedSources: number[]
  providerCode?: string
}
