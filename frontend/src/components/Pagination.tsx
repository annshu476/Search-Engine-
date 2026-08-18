import React from "react";

interface PaginationProps {
  currentPage: number; // 0-indexed
  totalPages: number;
  onPageChange: (page: number) => void;
}

export default function Pagination({
  currentPage,
  totalPages,
  onPageChange,
}: PaginationProps) {
  if (totalPages <= 1) return null;

  // Generate page range around current page
  const maxVisiblePages = 5;
  let startPage = Math.max(0, currentPage - Math.floor(maxVisiblePages / 2));
  let endPage = Math.min(totalPages - 1, startPage + maxVisiblePages - 1);

  if (endPage - startPage + 1 < maxVisiblePages) {
    startPage = Math.max(0, endPage - maxVisiblePages + 1);
  }

  const pages = [];
  for (let i = startPage; i <= endPage; i++) {
    pages.push(i);
  }

  return (
    <nav className="flex items-center justify-center space-x-1.5 py-8" aria-label="Pagination">
      {/* Previous Button */}
      <button
        onClick={() => onPageChange(currentPage - 1)}
        disabled={currentPage === 0}
        className="px-3.5 py-2 rounded border border-zinc-800 bg-zinc-900 text-sm font-mono font-medium text-zinc-400 hover:text-zinc-200 hover:border-zinc-700 disabled:opacity-40 disabled:hover:text-zinc-400 disabled:hover:border-zinc-800 disabled:cursor-not-allowed transition-all"
        aria-label="Previous page"
      >
        ‹ Prev
      </button>

      {/* First Page if startPage > 0 */}
      {startPage > 0 && (
        <>
          <button
            onClick={() => onPageChange(0)}
            className="w-10 h-10 rounded border border-zinc-800 bg-zinc-900 text-sm font-mono text-zinc-400 hover:text-zinc-200 hover:border-zinc-700 transition-all"
          >
            1
          </button>
          {startPage > 1 && (
            <span className="w-8 text-center text-zinc-600 font-mono text-sm">...</span>
          )}
        </>
      )}

      {/* Visible Pages */}
      {pages.map((page) => {
        const isCurrent = page === currentPage;
        return (
          <button
            key={page}
            onClick={() => onPageChange(page)}
            aria-current={isCurrent ? "page" : undefined}
            className={`w-10 h-10 rounded text-sm font-mono transition-all ${
              isCurrent
                ? "bg-zinc-100 text-zinc-950 font-bold border border-zinc-100"
                : "border border-zinc-800 bg-zinc-900 text-zinc-400 hover:text-zinc-200 hover:border-zinc-700"
            }`}
          >
            {page + 1}
          </button>
        );
      })}

      {/* Last Page if endPage < totalPages - 1 */}
      {endPage < totalPages - 1 && (
        <>
          {endPage < totalPages - 2 && (
            <span className="w-8 text-center text-zinc-600 font-mono text-sm">...</span>
          )}
          <button
            onClick={() => onPageChange(totalPages - 1)}
            className="w-10 h-10 rounded border border-zinc-800 bg-zinc-900 text-sm font-mono text-zinc-400 hover:text-zinc-200 hover:border-zinc-700 transition-all"
          >
            {totalPages}
          </button>
        </>
      )}

      {/* Next Button */}
      <button
        onClick={() => onPageChange(currentPage + 1)}
        disabled={currentPage === totalPages - 1}
        className="px-3.5 py-2 rounded border border-zinc-800 bg-zinc-900 text-sm font-mono font-medium text-zinc-400 hover:text-zinc-200 hover:border-zinc-700 disabled:opacity-40 disabled:hover:text-zinc-400 disabled:hover:border-zinc-800 disabled:cursor-not-allowed transition-all"
        aria-label="Next page"
      >
        Next ›
      </button>
    </nav>
  );
}
