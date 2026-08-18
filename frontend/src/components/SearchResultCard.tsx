import React from "react";
import { SearchResult } from "@/types/search";

interface SearchResultCardProps {
  result: SearchResult;
}

// Securely render text containing <em> highlight tags without dangerouslySetInnerHTML
export function renderHighlightedText(text: string): React.ReactNode[] | string {
  if (!text) return "";
  
  // Matches <em>...</em> groups
  const parts = text.split(/(<em>.*?<\/em>)/g);
  
  return parts.map((part, index) => {
    if (part.startsWith("<em>") && part.endsWith("</em>")) {
      const content = part.substring(4, part.length - 5);
      return (
        <em
          key={index}
          className="font-semibold text-zinc-100 bg-zinc-800 not-italic px-1 py-0.5 rounded-sm font-mono text-sm"
        >
          {content}
        </em>
      );
    }
    return part;
  });
}

export default function SearchResultCard({ result }: SearchResultCardProps) {
  // Determine which highlight or description to show
  // Hierarchy: title highlight -> headings highlight -> body highlight -> meta description -> URL
  const highlightedTitle = result.highlights.title?.[0]
    ? renderHighlightedText(result.highlights.title[0])
    : result.title || "Untitled Document";

  let highlightSnippet: React.ReactNode = null;
  if (result.highlights.bodyText?.[0]) {
    highlightSnippet = (
      <p className="text-sm text-zinc-400 leading-relaxed font-sans mt-1.5">
        ... {renderHighlightedText(result.highlights.bodyText[0])} ...
      </p>
    );
  } else if (result.highlights.headings?.[0]) {
    highlightSnippet = (
      <p className="text-sm text-zinc-400 leading-relaxed font-sans mt-1.5">
        [Heading] {renderHighlightedText(result.highlights.headings[0])}
      </p>
    );
  } else if (result.highlights.metaDescription?.[0]) {
    highlightSnippet = (
      <p className="text-sm text-zinc-400 leading-relaxed font-sans mt-1.5">
        {renderHighlightedText(result.highlights.metaDescription[0])}
      </p>
    );
  } else if (result.metaDescription) {
    highlightSnippet = (
      <p className="text-sm text-zinc-400 leading-relaxed font-sans mt-1.5">
        {result.metaDescription}
      </p>
    );
  }

  // Display domain name or full URL cleanly
  const displayUrl = result.url.length > 85 ? `${result.url.substring(0, 85)}...` : result.url;

  return (
    <article className="py-4 border-b border-zinc-900 last:border-0 hover:bg-zinc-900/10 rounded px-2 transition-colors">
      {/* Target URL Domain header */}
      <div className="flex items-center space-x-2 text-xs font-mono text-zinc-500 mb-1 truncate">
        <span className="text-zinc-600">›</span>
        <a
          href={result.url}
          target="_blank"
          rel="noopener noreferrer"
          className="hover:underline transition-all"
        >
          {displayUrl}
        </a>
      </div>

      {/* Document Title */}
      <h2 className="text-lg md:text-xl font-medium text-blue-400 hover:text-blue-300 transition-colors">
        <a href={result.url} target="_blank" rel="noopener noreferrer">
          {highlightedTitle}
        </a>
      </h2>

      {/* Snippet / Description */}
      {highlightSnippet}

      {/* Result Tags / Metadata */}
      <div className="flex flex-wrap gap-3 items-center text-xs font-mono text-zinc-500 mt-2.5 pt-1.5">
        {result.language && (
          <span className="bg-zinc-900 border border-zinc-800 text-zinc-400 px-2 py-0.5 rounded">
            lang: {result.language.toLowerCase()}
          </span>
        )}
        {result.wordCount !== undefined && result.wordCount !== null && (
          <span className="bg-zinc-900 border border-zinc-800 text-zinc-400 px-2 py-0.5 rounded">
            words: {result.wordCount}
          </span>
        )}
        {result.statusCode && (
          <span
            className={`px-2 py-0.5 rounded border ${
              result.statusCode >= 200 && result.statusCode < 300
                ? "bg-emerald-950/20 border-emerald-900/50 text-emerald-400"
                : "bg-amber-950/20 border-amber-900/50 text-amber-400"
            }`}
          >
            http: {result.statusCode}
          </span>
        )}
      </div>
    </article>
  );
}
