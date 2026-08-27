"use client";

import React, { useState } from "react";

export function SeedUrlInput() {
  const [url, setUrl] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<{
    accepted?: boolean;
    message?: string;
    normalizedUrl?: string;
    urlHash?: string;
    priority?: number;
    status?: number;
  } | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!url.trim()) return;

    setLoading(true);
    setResult(null);

    try {
      const res = await fetch("/api/urls", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url: url.trim() }),
      });

      const data = await res.json();
      setResult({
        ...data,
        status: res.status,
      });

      if (res.status === 202) {
        setUrl("");
      }
    } catch (err: any) {
      setResult({
        accepted: false,
        message: "Failed to connect to seed URL service",
        status: 503,
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full max-w-2xl mx-auto mt-8 p-4 bg-zinc-900/60 border border-zinc-800 rounded-lg text-left">
      <h3 className="text-xs font-mono font-semibold text-zinc-400 uppercase tracking-wider mb-2 flex items-center gap-2">
        <span className="w-2 h-2 rounded-full bg-emerald-500 inline-block animate-pulse"></span>
        Seed Crawl Frontier
      </h3>
      <p className="text-xs text-zinc-500 font-mono mb-3">
        Submit a new website URL to enter the automated crawl-discovery loop.
      </p>

      <form onSubmit={handleSubmit} className="flex gap-2">
        <input
          type="url"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="https://example.com"
          className="flex-1 px-3 py-2 bg-zinc-950 border border-zinc-800 focus:border-zinc-600 rounded text-sm text-zinc-100 placeholder-zinc-600 focus:outline-none font-mono"
        />
        <button
          type="submit"
          disabled={loading || !url.trim()}
          className="px-4 py-2 bg-emerald-700 hover:bg-emerald-600 disabled:opacity-50 text-white font-mono text-xs font-semibold rounded transition-colors flex items-center justify-center min-w-[90px]"
        >
          {loading ? "Seeding..." : "Submit URL"}
        </button>
      </form>

      {result && (
        <div
          className={`mt-3 p-3 rounded text-xs font-mono border ${
            result.accepted
              ? "bg-emerald-950/40 border-emerald-800/60 text-emerald-300"
              : result.status === 409
              ? "bg-amber-950/40 border-amber-800/60 text-amber-300"
              : "bg-red-950/40 border-red-800/60 text-red-300"
          }`}
        >
          <div className="font-bold mb-1 flex items-center justify-between">
            <span>
              {result.accepted
                ? "✓ 202 ACCEPTED — Queued for Crawling"
                : result.status === 409
                ? "⚠ 409 CONFLICT — URL Already Visited"
                : `✕ ${result.status || 400} ERROR — Submission Rejected`}
            </span>
            {result.priority !== undefined && (
              <span className="bg-emerald-900/60 px-1.5 py-0.5 rounded text-[10px]">
                Priority: {result.priority}
              </span>
            )}
          </div>
          {result.normalizedUrl && (
            <div className="truncate text-zinc-400">URL: {result.normalizedUrl}</div>
          )}
          {result.urlHash && (
            <div className="truncate text-zinc-500 text-[10px]">
              Hash: {result.urlHash}
            </div>
          )}
          {result.message && <div className="mt-1">{result.message}</div>}
        </div>
      )}
    </div>
  );
}
