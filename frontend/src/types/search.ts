export interface SearchFilters {
  language?: string;
  contentType?: string;
  statusCode?: number;
  fromDate?: string; // ISO format
  toDate?: string; // ISO format
}

export interface SearchResult {
  url: string;
  canonicalUrl: string;
  urlHash: string;
  title: string;
  metaDescription: string;
  language: string;
  wordCount?: number | null;
  statusCode?: number | null;
  highlights: Record<string, string[]>;
}

export interface SearchResponse {
  query: string;
  correctedQuery?: string | null;
  totalHits: number;
  page: number;
  size: number;
  totalPages: number;
  sort: string;
  results: SearchResult[];
}

export interface SearchSuggestionResponse {
  query: string;
  suggestions: string[];
}

export interface SearchErrorResponse {
  error: string;
  retryAfterSeconds?: number;
}
