"use client";

import React, { useState, useEffect, useRef, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Link from "next/link";
import { searchDocuments, getSearchSuggestions } from "@/lib/api/search";
import { SearchResponse, SearchFilters } from "@/types/search";
import SearchResultCard from "@/components/SearchResultCard";
import Pagination from "@/components/Pagination";

function SearchPageContent() {
  const searchParams = useSearchParams();
  const router = useRouter();

  // Read URL search params
  const query = searchParams.get("q") || "";
  const page = parseInt(searchParams.get("page") || "0", 10);
  const size = parseInt(searchParams.get("size") || "10", 10);
  const sort = searchParams.get("sort") || "relevance";
  const languageParam = searchParams.get("language") || "";
  const contentTypeParam = searchParams.get("contentType") || "";
  const statusCodeParam = searchParams.get("statusCode") || "";
  const fromDateParam = searchParams.get("fromDate") || "";
  const toDateParam = searchParams.get("toDate") || "";

  // Core API State
  const [data, setData] = useState<SearchResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<{ message: string; status?: number; retryAfterSeconds?: number } | null>(null);

  // Suggestion/Input State
  const [inputVal, setInputVal] = useState(query);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [isSuggestOpen, setIsSuggestOpen] = useState(false);
  const [isSuggestLoading, setIsSuggestLoading] = useState(false);
  const [suggestActiveIndex, setSuggestActiveIndex] = useState(-1);

  // Filters State
  const [showFilters, setShowFilters] = useState(false);
  const [filterLanguage, setFilterLanguage] = useState(languageParam);
  const [filterContentType, setFilterContentType] = useState(contentTypeParam);
  const [filterStatusCode, setFilterStatusCode] = useState(statusCodeParam);
  const [filterFromDate, setFilterFromDate] = useState(fromDateParam);
  const [filterToDate, setFilterToDate] = useState(toDateParam);

  const suggestContainerRef = useRef<HTMLDivElement>(null);
  const suggestDebounceRef = useRef<NodeJS.Timeout | null>(null);
  const suggestAbortRef = useRef<AbortController | null>(null);

  // Sync input value with URL changes (e.g. back/forward navigation)
  useEffect(() => {
    setInputVal(query);
  }, [query]);

  // Sync local filters with URL parameters
  useEffect(() => {
    setFilterLanguage(languageParam);
    setFilterContentType(contentTypeParam);
    setFilterStatusCode(statusCodeParam);
    setFilterFromDate(fromDateParam);
    setFilterToDate(toDateParam);
  }, [languageParam, contentTypeParam, statusCodeParam, fromDateParam, toDateParam]);

  // Close suggestions on click outside
  useEffect(() => {
    function clickOutside(event: MouseEvent) {
      if (suggestContainerRef.current && !suggestContainerRef.current.contains(event.target as Node)) {
        setIsSuggestOpen(false);
      }
    }
    document.addEventListener("mousedown", clickOutside);
    return () => document.removeEventListener("mousedown", clickOutside);
  }, []);

  // Fetch search results
  useEffect(() => {
    let active = true;
    setIsLoading(true);
    setError(null);

    const activeFilters: SearchFilters = {};
    if (languageParam.trim()) activeFilters.language = languageParam.trim();
    if (contentTypeParam.trim()) activeFilters.contentType = contentTypeParam.trim();
    if (statusCodeParam.trim()) {
      const code = parseInt(statusCodeParam.trim(), 10);
      if (!isNaN(code)) activeFilters.statusCode = code;
    }
    if (fromDateParam.trim()) activeFilters.fromDate = fromDateParam.trim();
    if (toDateParam.trim()) activeFilters.toDate = toDateParam.trim();

    const reqId = crypto.randomUUID();

    searchDocuments(query, page, size, sort, activeFilters, reqId)
      .then((res) => {
        if (active) {
          setData(res);
          setIsLoading(false);
        }
      })
      .catch((err) => {
        if (active) {
          console.error("Search failed:", err);
          setData(null);
          setIsLoading(false);
          setError({
            message: err.message || "An unexpected error occurred",
            status: err.status,
            retryAfterSeconds: err.retryAfterSeconds,
          });
        }
      });

    return () => {
      active = false;
    };
  }, [query, page, size, sort, languageParam, contentTypeParam, statusCodeParam, fromDateParam, toDateParam]);

  // Fetch Autocomplete Suggestions
  useEffect(() => {
    if (suggestDebounceRef.current) {
      clearTimeout(suggestDebounceRef.current);
    }

    const trimmed = inputVal.trim();
    if (trimmed.length < 2 || trimmed === query.trim()) {
      setSuggestions([]);
      setIsSuggestOpen(false);
      setIsSuggestLoading(false);
      return;
    }

    setIsSuggestLoading(true);
    setSuggestActiveIndex(-1);

    suggestDebounceRef.current = setTimeout(async () => {
      if (suggestAbortRef.current) {
        suggestAbortRef.current.abort();
      }

      suggestAbortRef.current = new AbortController();
      const requestId = crypto.randomUUID();

      try {
        const response = await getSearchSuggestions(trimmed, requestId);
        setSuggestions(response.suggestions);
        setIsSuggestOpen(response.suggestions.length > 0);
      } catch (err: any) {
        if (err.name !== "AbortError") {
          setSuggestions([]);
        }
      } finally {
        setIsSuggestLoading(false);
      }
    }, 150);

    return () => {
      if (suggestDebounceRef.current) clearTimeout(suggestDebounceRef.current);
      if (suggestAbortRef.current) suggestAbortRef.current.abort();
    };
  }, [inputVal, query]);

  // Handle Search Submission
  const handleSearchSubmit = (searchQuery: string) => {
    const trimmed = searchQuery.trim();
    if (!trimmed) return;
    setIsSuggestOpen(false);
    
    const params = new URLSearchParams(searchParams.toString());
    params.set("q", trimmed);
    params.set("page", "0"); // Reset page on new query
    router.push(`/search?${params.toString()}`);
  };

  const handleSuggestKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      if (suggestions.length > 0) {
        setSuggestActiveIndex((prev) => (prev + 1) % suggestions.length);
      }
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      if (suggestions.length > 0) {
        setSuggestActiveIndex((prev) => (prev - 1 + suggestions.length) % suggestions.length);
      }
    } else if (e.key === "Escape") {
      setIsSuggestOpen(false);
      setSuggestActiveIndex(-1);
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (suggestActiveIndex >= 0 && suggestActiveIndex < suggestions.length) {
        const selected = suggestions[suggestActiveIndex];
        setInputVal(selected);
        handleSearchSubmit(selected);
      } else {
        handleSearchSubmit(inputVal);
      }
    }
  };

  // Filter Management
  const applyFilters = (e: React.FormEvent) => {
    e.preventDefault();
    const params = new URLSearchParams();
    params.set("q", query);
    params.set("sort", sort);
    params.set("page", "0");

    if (filterLanguage.trim()) params.set("language", filterLanguage.trim());
    if (filterContentType.trim()) params.set("contentType", filterContentType.trim());
    if (filterStatusCode.trim()) params.set("statusCode", filterStatusCode.trim());
    if (filterFromDate.trim()) params.set("fromDate", filterFromDate.trim());
    if (filterToDate.trim()) params.set("toDate", filterToDate.trim());

    router.push(`/search?${params.toString()}`);
  };

  const clearFilters = () => {
    setFilterLanguage("");
    setFilterContentType("");
    setFilterStatusCode("");
    setFilterFromDate("");
    setFilterToDate("");
    router.push(`/search?q=${encodeURIComponent(query)}&sort=${sort}`);
  };

  // Sort Management
  const handleSortChange = (newSort: string) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("sort", newSort);
    params.set("page", "0"); // Reset to page 0
    router.push(`/search?${params.toString()}`);
  };

  // Page Management
  const handlePageChange = (newPage: number) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("page", newPage.toString());
    router.push(`/search?${params.toString()}`);
  };

  // Handle Retry on failures
  const handleRetry = () => {
    router.refresh();
  };

  return (
    <div className="flex-1 flex flex-col min-h-screen">
      {/* Top Header Bar */}
      <header className="sticky top-0 bg-zinc-950/90 backdrop-blur-md border-b border-zinc-900 z-40 px-4 md:px-8 py-3.5 flex flex-col md:flex-row md:items-center md:space-x-8 space-y-3.5 md:space-y-0">
        {/* Brand Link */}
        <Link href="/" className="text-xl font-bold font-mono tracking-tight text-white flex items-center space-x-1 hover:text-zinc-300">
          <span>Distributed Search</span>
        </Link>

        {/* Search Bar Input Container */}
        <div ref={suggestContainerRef} className="relative w-full max-w-xl flex-1">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSearchSubmit(inputVal);
            }}
            className="flex items-center bg-zinc-900 border border-zinc-800 focus-within:border-zinc-500 rounded-lg overflow-hidden transition-all"
          >
            <input
              type="text"
              value={inputVal}
              onChange={(e) => setInputVal(e.target.value)}
              onKeyDown={handleSuggestKeyDown}
              placeholder="Search..."
              className="w-full px-3.5 py-2.5 bg-transparent text-zinc-100 placeholder-zinc-500 focus:outline-none text-sm"
              autoComplete="off"
              role="combobox"
              aria-autocomplete="list"
              aria-expanded={isSuggestOpen}
            />

            {/* Small loading indicator */}
            {isSuggestLoading && (
              <div className="pr-3 flex items-center">
                <div className="w-3.5 h-3.5 border-2 border-zinc-500 border-t-transparent rounded-full animate-spin"></div>
              </div>
            )}

            <button
              type="submit"
              className="px-4 py-2.5 bg-zinc-850 hover:bg-zinc-800 text-zinc-300 font-mono text-xs border-l border-zinc-800 transition-colors"
            >
              Search
            </button>
          </form>

          {/* Autocomplete Dropdown */}
          {isSuggestOpen && suggestions.length > 0 && (
            <ul
              role="listbox"
              className="absolute left-0 right-0 mt-1.5 bg-zinc-900 border border-zinc-850 rounded-lg shadow-2xl overflow-hidden z-50 text-left"
            >
              {suggestions.map((suggestion, idx) => {
                const isActive = idx === suggestActiveIndex;
                return (
                  <li
                    key={suggestion}
                    role="option"
                    aria-selected={isActive}
                    onClick={() => {
                      setInputVal(suggestion);
                      handleSearchSubmit(suggestion);
                    }}
                    onMouseEnter={() => setSuggestActiveIndex(idx)}
                    className={`px-4 py-2.5 cursor-pointer text-xs transition-colors border-b border-zinc-900/50 last:border-0 ${
                      isActive
                        ? "bg-zinc-800 text-white"
                        : "text-zinc-300 hover:bg-zinc-850"
                    }`}
                  >
                    <span className="font-mono text-zinc-500 mr-2">›</span>
                    {suggestion}
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </header>

      {/* Main Page Layout */}
      <main className="flex-1 max-w-5xl w-full mx-auto px-4 md:px-8 py-6 flex flex-col md:flex-row md:space-x-8">
        {/* Results Stream Area */}
        <section className="flex-1 min-w-0 order-2 md:order-1">
          {/* 1. Loading State */}
          {isLoading && (
            <div className="space-y-6 pt-4">
              <div className="h-4 w-40 bg-zinc-900 rounded animate-pulse"></div>
              {[...Array(4)].map((_, i) => (
                <div key={i} className="py-4 border-b border-zinc-900 space-y-2.5">
                  <div className="h-4 w-1/4 bg-zinc-900 rounded animate-pulse"></div>
                  <div className="h-6 w-3/4 bg-zinc-900 rounded animate-pulse"></div>
                  <div className="h-4 w-5/6 bg-zinc-900 rounded animate-pulse"></div>
                  <div className="h-4 w-1/2 bg-zinc-900 rounded animate-pulse"></div>
                </div>
              ))}
            </div>
          )}

          {/* 2. Error State */}
          {!isLoading && error && (
            <div className="py-12 px-6 bg-zinc-900/30 border border-zinc-900 rounded-lg text-center max-w-xl mx-auto mt-6">
              <div className="text-rose-500 text-2xl mb-3">⚠</div>
              <h3 className="text-md font-mono text-zinc-200 mb-2">
                {error.status === 429
                  ? "Too many requests"
                  : error.status === 400
                  ? "Search request could not be completed"
                  : "Search service unavailable"}
              </h3>
              <p className="text-sm text-zinc-400 font-sans mb-6">
                {error.status === 429
                  ? `Too many searches. Please try again shortly. ${
                      error.retryAfterSeconds ? `(Retry after ${error.retryAfterSeconds}s)` : ""
                    }`
                  : error.message}
              </p>
              <button
                onClick={handleRetry}
                className="px-5 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-xs font-mono rounded transition-colors"
              >
                Retry
              </button>
            </div>
          )}

          {/* 3. Success State with Results */}
          {!isLoading && !error && data && (
            <div className="space-y-6">
              {/* Metadata Banner */}
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between text-xs font-mono text-zinc-500 border-b border-zinc-900 pb-3">
                <div>
                  About {data.totalHits} results (page {data.page + 1} of {data.totalPages || 1})
                </div>
                {/* Sort Selector */}
                <div className="flex items-center space-x-2 mt-2 sm:mt-0">
                  <span>Sort by:</span>
                  <button
                    onClick={() => handleSortChange("relevance")}
                    className={`font-semibold hover:text-zinc-200 ${
                      sort === "relevance" ? "text-zinc-100 underline decoration-zinc-500 decoration-2" : "text-zinc-500"
                    }`}
                  >
                    Relevance
                  </button>
                  <span>|</span>
                  <button
                    onClick={() => handleSortChange("newest")}
                    className={`font-semibold hover:text-zinc-200 ${
                      sort === "newest" ? "text-zinc-100 underline decoration-zinc-500 decoration-2" : "text-zinc-500"
                    }`}
                  >
                    Newest
                  </button>
                </div>
              </div>

              {/* Spell Correction Display */}
              {data.correctedQuery && (
                <div className="bg-zinc-900/20 border border-zinc-900 rounded p-4 text-sm font-sans space-y-1">
                  <div className="text-zinc-400">
                    Showing results for:{" "}
                    <Link
                      href={`/search?q=${encodeURIComponent(data.correctedQuery)}&sort=${sort}`}
                      className="text-blue-400 hover:underline font-medium italic"
                    >
                      {data.correctedQuery}
                    </Link>
                  </div>
                  <div className="text-xs text-zinc-500">
                    Search instead for:{" "}
                    <Link
                      href={`/search?q=${encodeURIComponent(query)}&sort=${sort}`}
                      className="hover:underline italic text-zinc-400"
                    >
                      {query}
                    </Link>
                  </div>
                </div>
              )}

              {/* Empty state (totalHits === 0) */}
              {data.totalHits === 0 && (
                <div className="py-12 text-left max-w-xl font-sans">
                  <h3 className="text-zinc-200 font-mono text-sm mb-4">
                    No results found for &ldquo;{query}&rdquo;
                  </h3>
                  <p className="text-sm text-zinc-400 mb-2">Suggestions:</p>
                  <ul className="list-disc pl-5 text-sm text-zinc-500 space-y-1">
                    <li>Check your spelling.</li>
                    <li>Try fewer or different keywords.</li>
                    <li>Try removing active search filters.</li>
                    <li>Try a broader search query.</li>
                  </ul>
                </div>
              )}

              {/* Results cards stream */}
              {data.totalHits > 0 && (
                <div className="divide-y divide-zinc-900">
                  {data.results.map((result, idx) => (
                    <SearchResultCard key={`${result.urlHash}-${idx}`} result={result} />
                  ))}
                </div>
              )}

              {/* Pagination */}
              {data.totalHits > 0 && (
                <Pagination
                  currentPage={data.page}
                  totalPages={data.totalPages}
                  onPageChange={handlePageChange}
                />
              )}
            </div>
          )}
        </section>

        {/* Expandable Sidebar Filter Panel */}
        <aside className="w-full md:w-64 order-1 md:order-2 mb-6 md:mb-0">
          <div className="border border-zinc-900 bg-zinc-900/10 rounded-lg p-4 space-y-4">
            <button
              onClick={() => setShowFilters(!showFilters)}
              className="flex items-center justify-between w-full font-mono text-xs text-zinc-400 uppercase tracking-wider focus:outline-none hover:text-zinc-200 transition-colors"
            >
              <span>Search Filters</span>
              <span className="font-sans text-sm">{showFilters ? "−" : "+"}</span>
            </button>

            {/* Filter Content */}
            <form
              onSubmit={applyFilters}
              className={`space-y-4 pt-1 transition-all ${
                showFilters || languageParam || contentTypeParam || statusCodeParam || fromDateParam || toDateParam
                  ? "block"
                  : "hidden md:block"
              }`}
            >
              {/* Language */}
              <div className="space-y-1.5">
                <label htmlFor="filter-lang" className="block text-xs font-mono text-zinc-500">
                  Language code
                </label>
                <input
                  id="filter-lang"
                  type="text"
                  value={filterLanguage}
                  onChange={(e) => setFilterLanguage(e.target.value)}
                  placeholder="e.g. en, de, fr"
                  className="w-full px-2.5 py-1.5 bg-zinc-950 border border-zinc-900 focus:border-zinc-700 rounded text-xs text-zinc-200 placeholder-zinc-700 focus:outline-none"
                />
              </div>

              {/* Content-Type */}
              <div className="space-y-1.5">
                <label htmlFor="filter-type" className="block text-xs font-mono text-zinc-500">
                  Content-Type
                </label>
                <input
                  id="filter-type"
                  type="text"
                  value={filterContentType}
                  onChange={(e) => setFilterContentType(e.target.value)}
                  placeholder="e.g. text/html"
                  className="w-full px-2.5 py-1.5 bg-zinc-950 border border-zinc-900 focus:border-zinc-700 rounded text-xs text-zinc-200 placeholder-zinc-700 focus:outline-none"
                />
              </div>

              {/* Status Code */}
              <div className="space-y-1.5">
                <label htmlFor="filter-status" className="block text-xs font-mono text-zinc-500">
                  HTTP Status code
                </label>
                <input
                  id="filter-status"
                  type="number"
                  min="100"
                  max="599"
                  value={filterStatusCode}
                  onChange={(e) => setFilterStatusCode(e.target.value)}
                  placeholder="e.g. 200"
                  className="w-full px-2.5 py-1.5 bg-zinc-950 border border-zinc-900 focus:border-zinc-700 rounded text-xs text-zinc-200 placeholder-zinc-700 focus:outline-none font-mono"
                />
              </div>

              {/* From Date */}
              <div className="space-y-1.5">
                <label htmlFor="filter-from" className="block text-xs font-mono text-zinc-500">
                  From Date (ISO)
                </label>
                <input
                  id="filter-from"
                  type="text"
                  value={filterFromDate}
                  onChange={(e) => setFilterFromDate(e.target.value)}
                  placeholder="e.g. 2026-08-01T00:00:00Z"
                  className="w-full px-2.5 py-1.5 bg-zinc-950 border border-zinc-900 focus:border-zinc-700 rounded text-xs text-zinc-200 placeholder-zinc-700 focus:outline-none font-mono"
                />
              </div>

              {/* To Date */}
              <div className="space-y-1.5">
                <label htmlFor="filter-to" className="block text-xs font-mono text-zinc-500">
                  To Date (ISO)
                </label>
                <input
                  id="filter-to"
                  type="text"
                  value={filterToDate}
                  onChange={(e) => setFilterToDate(e.target.value)}
                  placeholder="e.g. 2026-08-18T23:59:59Z"
                  className="w-full px-2.5 py-1.5 bg-zinc-950 border border-zinc-900 focus:border-zinc-700 rounded text-xs text-zinc-200 placeholder-zinc-700 focus:outline-none font-mono"
                />
              </div>

              {/* Action Buttons */}
              <div className="flex space-x-2 pt-2">
                <button
                  type="submit"
                  className="flex-1 px-3 py-2 bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 text-zinc-200 text-xs font-mono rounded transition-colors"
                >
                  Apply
                </button>
                <button
                  type="button"
                  onClick={clearFilters}
                  className="px-3 py-2 bg-transparent hover:bg-zinc-900 text-zinc-500 hover:text-zinc-300 text-xs font-mono rounded border border-zinc-900 transition-colors"
                >
                  Clear
                </button>
              </div>
            </form>
          </div>
        </aside>
      </main>
    </div>
  );
}

export default function SearchPage() {
  return (
    <Suspense
      fallback={
        <div className="flex-1 flex items-center justify-center min-h-screen">
          <div className="text-zinc-400 font-mono text-sm">Loading Search Engine...</div>
        </div>
      }
    >
      <SearchPageContent />
    </Suspense>
  );
}
