import { SearchFilters, SearchResponse, SearchSuggestionResponse } from "@/types/search";

const getBaseUrl = (): string => {
  if (typeof window === "undefined") {
    // Server-side: target the indexer service directly
    return process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8083";
  }
  // Client-side: use the Next.js route proxy to avoid CORS
  return "";
};

export async function searchDocuments(
  query: string,
  page: number = 0,
  size: number = 10,
  sort: string = "relevance",
  filters: SearchFilters = {},
  requestId?: string
): Promise<SearchResponse> {
  const baseUrl = getBaseUrl();
  const params = new URLSearchParams();
  
  if (query) params.set("q", query);
  params.set("page", page.toString());
  params.set("size", size.toString());
  params.set("sort", sort);

  if (filters.language) params.set("language", filters.language);
  if (filters.contentType) params.set("contentType", filters.contentType);
  if (filters.statusCode !== undefined && filters.statusCode !== null) {
    params.set("statusCode", filters.statusCode.toString());
  }
  if (filters.fromDate) params.set("fromDate", filters.fromDate);
  if (filters.toDate) params.set("toDate", filters.toDate);

  // Use the appropriate endpoint path:
  // On the server, we fetch from direct Indexer API: /api/search
  // On the client, we fetch from local proxy: /api/search
  const url = `${baseUrl}/api/search?${params.toString()}`;

  const headers: Record<string, string> = {
    "Accept": "application/json",
  };
  if (requestId) {
    headers["X-Request-Id"] = requestId;
  }

  const response = await fetch(url, {
    method: "GET",
    headers,
    cache: "no-store", // Cache is handled by the backend
  });

  const data = await response.json();

  if (!response.ok) {
    // Return structured error
    const errorMsg = data.error || "Search service returned an error";
    const error = new Error(errorMsg) as any;
    error.status = response.status;
    error.retryAfterSeconds = response.headers.get("Retry-After") 
      ? parseInt(response.headers.get("Retry-After")!, 10) 
      : undefined;
    throw error;
  }

  return data as SearchResponse;
}

export async function getSearchSuggestions(
  prefix: string,
  requestId?: string
): Promise<SearchSuggestionResponse> {
  const baseUrl = getBaseUrl();
  // On server: use direct Indexer API suggest endpoint
  // On client: use local proxy suggest endpoint
  const endpoint = typeof window === "undefined" 
    ? `${baseUrl}/api/search/suggest` 
    : `${baseUrl}/api/suggest`;
  
  const url = `${endpoint}?q=${encodeURIComponent(prefix)}`;

  const headers: Record<string, string> = {
    "Accept": "application/json",
  };
  if (requestId) {
    headers["X-Request-Id"] = requestId;
  }

  const response = await fetch(url, {
    method: "GET",
    headers,
  });

  const data = await response.json();

  if (!response.ok) {
    const errorMsg = data.error || "Search suggestion service returned an error";
    const error = new Error(errorMsg) as any;
    error.status = response.status;
    throw error;
  }

  return data as SearchSuggestionResponse;
}
