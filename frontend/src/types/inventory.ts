export type StockState = 'IN_STOCK' | 'OUT_OF_STOCK' | 'INACTIVE'

export interface Inventory {
  productId: string
  availableStock: number
  lockedStock: number
  stockState: StockState
}
