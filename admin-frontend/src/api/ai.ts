import { get, post } from './http'
import type {
  AiAnalysisResponse,
  AiDailyInventoryReport,
  AiHotProductsAnalysisRequest,
  AiInventoryAskRequest,
  AiInventoryAskResponse,
  AiLowStockAnalysisRequest,
  AiSuggestion,
  AiSuggestionGenerateRequest,
} from '@/types/ai'

// Phase 3 AI analysis APIs. Gateway-only: /api/admin/ai/** — never a service
// port and never an internal-only route. These endpoints are read/analyze
// only; the generate endpoint persists a PENDING_REVIEW suggestion and never
// touches stock.
export function askInventoryQuestion(
  payload: AiInventoryAskRequest,
): Promise<AiInventoryAskResponse> {
  return post<AiInventoryAskResponse>('/api/admin/ai/inventory/ask', payload)
}

export function runLowStockAnalysis(
  payload: AiLowStockAnalysisRequest = {},
): Promise<AiAnalysisResponse> {
  return post<AiAnalysisResponse>('/api/admin/ai/inventory/low-stock-analysis', payload)
}

export function runHotProductsAnalysis(
  payload: AiHotProductsAnalysisRequest,
): Promise<AiAnalysisResponse> {
  return post<AiAnalysisResponse>('/api/admin/ai/inventory/hot-products-analysis', payload)
}

export function generateReplenishmentSuggestion(
  payload: AiSuggestionGenerateRequest = {},
): Promise<AiSuggestion> {
  return post<AiSuggestion>('/api/admin/ai/replenishment-suggestions/generate', payload)
}

export function getAiDailyReport(): Promise<AiDailyInventoryReport> {
  return get<AiDailyInventoryReport>('/api/admin/ai/reports/daily')
}
