export interface ErrorResponse {
  code: string;
  message: string;
  details?: string[];
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}
