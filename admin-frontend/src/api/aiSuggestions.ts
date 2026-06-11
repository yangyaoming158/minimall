import { get, post } from './http'
import type { PageResponse } from '@/types/api'
import type {
  AiSuggestion,
  AiSuggestionListParams,
  RejectAiSuggestionRequest,
} from '@/types/ai'

// Phase 2.5 suggestion review APIs (reused, NOT duplicated under
// /api/admin/ai/suggestions/**). Gateway-only. Review actions never change
// stock: reject closes the suggestion, convert only creates an inbound DRAFT.
export function listAiSuggestions(
  params: AiSuggestionListParams,
): Promise<PageResponse<AiSuggestion>> {
  return get<PageResponse<AiSuggestion>>('/api/admin/ai-suggestions', { params })
}

export function getAiSuggestion(suggestionNo: string): Promise<AiSuggestion> {
  return get<AiSuggestion>(`/api/admin/ai-suggestions/${encodeURIComponent(suggestionNo)}`)
}

export function rejectAiSuggestion(
  suggestionNo: string,
  payload: RejectAiSuggestionRequest,
): Promise<AiSuggestion> {
  return post<AiSuggestion>(
    `/api/admin/ai-suggestions/${encodeURIComponent(suggestionNo)}/reject`,
    payload,
  )
}

export function convertAiSuggestionToInboundDraft(suggestionNo: string): Promise<AiSuggestion> {
  return post<AiSuggestion>(
    `/api/admin/ai-suggestions/${encodeURIComponent(suggestionNo)}/convert-inbound-draft`,
  )
}
