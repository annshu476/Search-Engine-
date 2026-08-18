import React from "react";
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import SearchResultCard, { renderHighlightedText } from "@/components/SearchResultCard";
import Pagination from "@/components/Pagination";

// Mocking simple search result data
const mockResult = {
  url: "https://example.com/java-spring",
  canonicalUrl: "https://example.com/java-spring",
  urlHash: "abc123hash",
  title: "Java Spring Boot Application",
  metaDescription: "Learn how to build a spring boot application.",
  language: "en",
  wordCount: 420,
  statusCode: 200,
  highlights: {},
};

const mockResultWithHighlights = {
  ...mockResult,
  highlights: {
    title: ["Java <em>Spring</em> Boot Application"],
    bodyText: ["Learn how to build a <em>spring</em> boot app quickly."],
  },
};

describe("SearchResultCard Component", () => {
  it("renders basic document metadata correctly", () => {
    render(<SearchResultCard result={mockResult} />);
    
    // Check title and url
    expect(screen.getByText("Java Spring Boot Application")).toBeInTheDocument();
    expect(screen.getByText("https://example.com/java-spring")).toBeInTheDocument();
    
    // Check snippet description
    expect(screen.getByText("Learn how to build a spring boot application.")).toBeInTheDocument();
    
    // Check metadata tags
    expect(screen.getByText("lang: en")).toBeInTheDocument();
    expect(screen.getByText("words: 420")).toBeInTheDocument();
    expect(screen.getByText("http: 200")).toBeInTheDocument();
  });

  it("safely and correctly renders em highlighted texts", () => {
    render(<SearchResultCard result={mockResultWithHighlights} />);
    
    // The highlighted element should be present with correct classes
    const highlightedItems = screen.getAllByText("Spring");
    expect(highlightedItems.length).toBeGreaterThan(0);
    expect(highlightedItems[0].tagName).toBe("EM");
    expect(highlightedItems[0]).toHaveClass("bg-zinc-800");

    const bodyHighlightedItems = screen.getAllByText("spring");
    expect(bodyHighlightedItems.length).toBeGreaterThan(0);
    expect(bodyHighlightedItems[0].tagName).toBe("EM");
  });

  it("handles renderHighlightedText helper securely without HTML injection", () => {
    const textWithScript = "Normal text <em>spring</em> <script>alert('xss')</script>";
    const rendered = renderHighlightedText(textWithScript) as any[];
    
    // Rendered elements should be React nodes, not executed HTML script strings
    expect(rendered).toBeInstanceOf(Array);
    
    // Verify it split the components correctly
    const emNode = rendered.find((node: any) => node && node.type === "em");
    expect(emNode).toBeDefined();
    expect(emNode.props.children).toBe("spring");
  });
});

describe("Pagination Component", () => {
  it("renders page buttons and handles events", () => {
    const onPageChange = vi.fn();
    render(<Pagination currentPage={2} totalPages={10} onPageChange={onPageChange} />);

    // Active page is 3 (index 2)
    const activeBtn = screen.getByRole("button", { name: "3" });
    expect(activeBtn).toHaveAttribute("aria-current", "page");

    // Click next page button
    const nextBtn = screen.getByRole("button", { name: "Next page" });
    fireEvent.click(nextBtn);
    expect(onPageChange).toHaveBeenCalledWith(3);

    // Click page 5 button
    const page5Btn = screen.getByRole("button", { name: "5" });
    fireEvent.click(page5Btn);
    expect(onPageChange).toHaveBeenCalledWith(4);
  });

  it("disables previous and next buttons correctly at boundary ranges", () => {
    const onPageChange = vi.fn();
    
    // First page
    const { rerender } = render(
      <Pagination currentPage={0} totalPages={5} onPageChange={onPageChange} />
    );
    expect(screen.getByRole("button", { name: "Previous page" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Next page" })).not.toBeDisabled();

    // Last page
    rerender(
      <Pagination currentPage={4} totalPages={5} onPageChange={onPageChange} />
    );
    expect(screen.getByRole("button", { name: "Previous page" })).not.toBeDisabled();
    expect(screen.getByRole("button", { name: "Next page" })).toBeDisabled();
  });
});
