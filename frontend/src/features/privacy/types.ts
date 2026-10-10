export interface LegalDocument {
  version: string
  summary: string
  effectiveAt: string
}

export interface LegalCurrent {
  terms: LegalDocument
  privacy: LegalDocument
}
