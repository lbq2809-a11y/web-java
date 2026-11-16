export interface BatchResponse {
  id: string;
  medicineId: string;
  batchNumber: string;
  expiryDate: string;
  quantityIn: number;
  quantityAvailable: number;
  costPrice: number;
  receivedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface StockResponse {
  medicineId: string;
  totalAvailable: number;
  batches: BatchResponse[];
}

export interface StockCheckItem {
  medicineId: string;
  quantity: number;
}

export interface StockCheckRequest {
  items: StockCheckItem[];
}

export interface StockCheckItemResult {
  medicineId: string;
  requested: number;
  available: number;
  sufficient: boolean;
}

export interface StockCheckResponse {
  allAvailable: boolean;
  items: StockCheckItemResult[];
}
