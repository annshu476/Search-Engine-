"use client";

import React, { useState, useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import { getSearchSuggestions } from "@/lib/api/search";

export default function Home() {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [isOpen, setIsOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  
  const containerRef = useRef<HTMLDivElement>(null);
  const debounceTimerRef = useRef<NodeJS.Timeout | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);

  // Close dropdown when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // Fetch suggestions with debounce & cancellation
  useEffect(() => {
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }

    const trimmed = query.trim();
    if (trimmed.length < 2) {
      setSuggestions([]);
      setIsOpen(false);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setActiveIndex(-1);

    debounceTimerRef.current = setTimeout(async () => {
      // Cancel previous request if still running
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }

      abortControllerRef.current = new AbortController();
      const requestId = crypto.randomUUID();

      try {
        const response = await getSearchSuggestions(trimmed, requestId);
        setSuggestions(response.suggestions);
        setIsOpen(response.suggestions.length > 0);
      } catch (err: any) {
        if (err.name !== "AbortError") {
          console.error("Failed to load suggestions:", err);
          setSuggestions([]);
        }
      } finally {
        setIsLoading(false);
      }
    }, 150); // 150ms debounce delay

    return () => {
      if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);
      if (abortControllerRef.current) abortControllerRef.current.abort();
    };
  }, [query]);

  const handleSearchSubmit = (searchQuery: string) => {
    const trimmed = searchQuery.trim();
    if (!trimmed) return;
    setIsOpen(false);
    router.push(`/search?q=${encodeURIComponent(trimmed)}`);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      if (suggestions.length > 0) {
        setActiveIndex((prev) => (prev + 1) % suggestions.length);
      }
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      if (suggestions.length > 0) {
        setActiveIndex((prev) => (prev - 1 + suggestions.length) % suggestions.length);
      }
    } else if (e.key === "Escape") {
      setIsOpen(false);
      setActiveIndex(-1);
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (activeIndex >= 0 && activeIndex < suggestions.length) {
        const selected = suggestions[activeIndex];
        setQuery(selected);
        handleSearchSubmit(selected);
      } else {
        handleSearchSubmit(query);
      }
    }
  };

  return (
    <div className="flex-1 flex flex-col items-center justify-center px-4 relative min-h-screen">
      <div className="w-full max-w-2xl text-center space-y-8 -mt-24">
        {/* Title & Branding */}
        <div className="space-y-3">
          <h1 className="text-4xl md:text-5xl font-extrabold tracking-tight text-white font-mono">
            Distributed Search
          </h1>
          <p className="text-sm md:text-base text-zinc-400 font-mono tracking-wide max-w-lg mx-auto">
            Search across the web with a distributed event-driven search engine.
          </p>
        </div>

        {/* Search Container */}
        <div ref={containerRef} className="relative w-full">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSearchSubmit(query);
            }}
            className="flex items-center bg-zinc-900 border border-zinc-800 hover:border-zinc-700 focus-within:border-zinc-500 rounded-lg overflow-hidden transition-colors"
          >
            {/* Search Icon */}
            <div className="pl-4 text-zinc-500">
              <svg
                className="w-5 h-5"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth="2"
                  d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                />
              </svg>
            </div>

            {/* Input */}
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Search the web..."
              className="w-full px-4 py-4 bg-transparent text-zinc-100 placeholder-zinc-500 focus:outline-none text-base"
              autoComplete="off"
              role="combobox"
              aria-autocomplete="list"
              aria-expanded={isOpen}
              aria-controls="autocomplete-suggestions"
              autoFocus
            />

            {/* Loading Indicator */}
            {isLoading && (
              <div className="pr-4 flex items-center justify-center">
                <div className="w-4 h-4 border-2 border-zinc-500 border-t-transparent rounded-full animate-spin"></div>
              </div>
            )}

            {/* Submit Button */}
            <button
              type="submit"
              className="px-6 py-4 bg-zinc-850 hover:bg-zinc-800 text-zinc-300 font-mono text-sm border-l border-zinc-800 transition-colors"
            >
              Search
            </button>
          </form>

          {/* Autocomplete Dropdown */}
          {isOpen && suggestions.length > 0 && (
            <ul
              id="autocomplete-suggestions"
              role="listbox"
              className="absolute left-0 right-0 mt-2 bg-zinc-900 border border-zinc-850 rounded-lg shadow-2xl overflow-hidden z-50 text-left"
            >
              {suggestions.map((suggestion, idx) => {
                const isActive = idx === activeIndex;
                return (
                  <li
                    key={suggestion}
                    role="option"
                    aria-selected={isActive}
                    onClick={() => {
                      setQuery(suggestion);
                      handleSearchSubmit(suggestion);
                    }}
                    onMouseEnter={() => setActiveIndex(idx)}
                    className={`px-5 py-3 cursor-pointer text-sm transition-colors border-b border-zinc-900/50 last:border-0 ${
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

        {/* Tech Stack Info */}
        <div className="flex flex-wrap justify-center items-center gap-4 text-xs font-mono text-zinc-600 pt-8 border-t border-zinc-900">
          <span>Java 21</span>
          <span>•</span>
          <span>Spring Boot 3.5</span>
          <span>•</span>
          <span>Kafka</span>
          <span>•</span>
          <span>Elasticsearch</span>
          <span>•</span>
          <span>Redis</span>
        </div>
      </div>
    </div>
  );
}
