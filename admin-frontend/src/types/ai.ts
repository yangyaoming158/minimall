export type AiAnalysisType = 'INVENTORY_QA' | 'LOW_STOCK' | 'HOT_PRODUCTS' | 'REPLENISHMENT'

export type AiInventoryQuestionIntent =
  | 'CURRENT_STOCK'
  | 'LOW_STOCK_LIST'
  | 'PRODUCT_STATUS'
  | 'RECENT_RECORDS'
  | 'UNSUPPORTED'

export type AiInventoryEvidenceType =
  | 'CURRENT_INVENTORY'
  | 'LOW_STOCK_CANDIDATES'
  | 'LOW_STOCK_ANALYSIS'
  | 'HOT_PRODUCTS'

export type AiSuggestionRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export type AiSuggestionStatus = 'PENDING_REVIEW' | 'CONVERTED_TO_DRAFT' | 'REJECTED' | 'APPLIED'

export type AiSuggestionType = 'REPLENISHMENT' | 'DAILY_REPORT'

export type AiSuggestionSource = 'AI_MODEL' | 'SYSTEM_GENERATED' | 'ADMIN_MANUAL'

export interface AiInventoryItemEvidence {
  productId: string
  availableStock: number
  lockedStock: number
  safetyStock: number
  status: 'ACTIVE' | 'INACTIVE'
  stockState: 'IN_STOCK' | 'OUT_OF_STOCK' | 'INACTIVE'
  lowStock: boolean
  createdAt: string
  updatedAt: string
}

export interface AiInventoryRecordEvidence {
  id: number
  productId: string
  orderNo: string | null
  requestId: string | null
  changeType: 'DEDUCT' | 'RELEASE' | 'ADJUST_INCREASE' | 'ADJUST_DECREASE'
  sourceType:
    | 'ORDER_DEDUCT'
    | 'ORDER_RELEASE'
    | 'ADMIN_INITIALIZE'
    | 'ADMIN_ADJUSTMENT'
    | 'INBOUND_ORDER'
    | 'AI_SUGGESTION'
  quantity: number
  reason: string | null
  adminUserId: number | null
  adminUsername: string | null
  referenceNo: string | null
  status: 'SUCCESS'
  createdAt: string
  updatedAt: string
}

export interface AiSalesEvidence {
  productId: string
  soldQuantity: number
  orderCount: number
  totalAmount: string | number
}

export interface AiInventoryEvidence {
  evidenceType: AiInventoryEvidenceType
  generatedAt: string
  dataFrom: string | null
  dataTo: string | null
  inventories: AiInventoryItemEvidence[]
  records: AiInventoryRecordEvidence[]
}

export interface AiInventorySalesItemEvidence {
  productId: string
  rank: number
  inventory: AiInventoryItemEvidence | null
  sales: AiSalesEvidence | null
  records: AiInventoryRecordEvidence[]
  limitations: string[]
}

export interface AiInventorySalesEvidence {
  evidenceType: AiInventoryEvidenceType
  days: number
  generatedAt: string
  dataFrom: string | null
  dataTo: string | null
  limitations: string[]
  products: AiInventorySalesItemEvidence[]
}

export interface AiInventoryAskRequest {
  question: string
  productId?: string
  limit?: number
  recordLimit?: number
}

export interface AiInventoryAskResponse {
  intent: AiInventoryQuestionIntent
  supported: boolean
  answer: string
  queryTime: string
  evidence: AiInventoryEvidence | null
  limitations: string[]
}

export interface AiLowStockAnalysisRequest {
  limit?: number
  recordLimit?: number
}

export interface AiHotProductsAnalysisRequest {
  days: 7 | 30
  limit?: number
  recordLimit?: number
}

export interface AiAnalysisItem {
  productId: string
  productName: string | null
  availableStock: number | null
  lockedStock: number | null
  safetyStock: number | null
  soldQuantityLast7Days: number | null
  riskLevel: AiSuggestionRiskLevel | null
  reason: string | null
}

export interface AiAnalysisResponse {
  analysisType: AiAnalysisType
  summary: string
  queryTime: string
  evidence: AiInventorySalesEvidence | null
  items: AiAnalysisItem[]
  limitations: string[]
}

export interface AiSuggestionGenerateRequest {
  limit?: number
  recordLimit?: number
}

export interface AiSuggestionItem {
  productId: string
  productName: string | null
  availableStock: number | null
  lockedStock: number | null
  safetyStock: number | null
  soldQuantityLast7Days: number | null
  suggestedQuantity: number
  riskLevel: AiSuggestionRiskLevel | null
  reason: string | null
}

export interface AiSuggestion {
  suggestionNo: string
  type: AiSuggestionType
  status: AiSuggestionStatus
  source: AiSuggestionSource
  reason: string | null
  inputSnapshotRef: string | null
  inputSummary: string | null
  linkedInboundNo: string | null
  rejectedReason: string | null
  reviewedByAdminUserId: number | null
  reviewedByAdminUsername: string | null
  reviewedAt: string | null
  itemCount: number
  totalSuggestedQuantity: number
  items: AiSuggestionItem[]
  createdAt: string
  updatedAt: string
}

export interface AiSuggestionListParams {
  status?: AiSuggestionStatus
  page?: number
  size?: number
  sort?: string
}

export interface RejectAiSuggestionRequest {
  reason: string
}
