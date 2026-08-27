package com.searchengine.contentprocessor.parser;

import com.searchengine.contentprocessor.model.kafka.DiscoveredUrl;
import com.searchengine.contentprocessor.model.kafka.RawHtmlDocument;
import com.searchengine.contentprocessor.model.kafka.SearchDocument;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parser component that converts a RawHtmlDocument into a structured SearchDocument using Jsoup.
 */
@Component
public class HtmlDocumentParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(HtmlDocumentParser.class);

    public SearchDocument parse(RawHtmlDocument rawHtmlDocument) {
        if (rawHtmlDocument == null) {
            LOGGER.warn("HTML_PARSER_NULL_DOCUMENT input rawHtmlDocument is null");
            return null;
        }

        Instant indexedAt = Instant.now();
        String fallbackCanonicalUrl = rawHtmlDocument.finalUrl() != null && !rawHtmlDocument.finalUrl().isBlank()
                ? rawHtmlDocument.finalUrl()
                : rawHtmlDocument.url();

        if (rawHtmlDocument.html() == null || rawHtmlDocument.html().isBlank()) {
            return new SearchDocument(
                    rawHtmlDocument.url(),
                    fallbackCanonicalUrl,
                    rawHtmlDocument.urlHash(),
                    null,
                    null,
                    Collections.emptyList(),
                    "",
                    null,
                    0,
                    rawHtmlDocument.statusCode(),
                    rawHtmlDocument.contentType(),
                    rawHtmlDocument.fetchedAt(),
                    indexedAt
            );
        }

        String baseUri = fallbackCanonicalUrl != null ? fallbackCanonicalUrl : "";
        Document doc = Jsoup.parse(rawHtmlDocument.html(), baseUri);

        String canonicalUrl = extractCanonicalUrl(doc, fallbackCanonicalUrl);
        String title = extractTitle(doc);
        String metaDescription = extractMetaDescription(doc);
        List<String> headings = extractHeadings(doc);

        // Remove script, style, noscript, template elements prior to body text extraction
        doc.select("script, style, noscript, template").remove();

        String bodyText = extractBodyText(doc);
        int wordCount = calculateWordCount(bodyText);
        String language = extractLanguage(doc);

        return new SearchDocument(
                rawHtmlDocument.url(),
                canonicalUrl,
                rawHtmlDocument.urlHash(),
                title,
                metaDescription,
                headings,
                bodyText,
                language,
                wordCount,
                rawHtmlDocument.statusCode(),
                rawHtmlDocument.contentType(),
                rawHtmlDocument.fetchedAt(),
                indexedAt
        );
    }

    private String extractCanonicalUrl(Document doc, String fallbackUrl) {
        Elements canonicalLinks = doc.select("link[rel~=(?i)^canonical$]");
        for (Element link : canonicalLinks) {
            String href = link.attr("href").trim();
            if (!href.isBlank()) {
                String absUrl = link.absUrl("href").trim();
                if (!absUrl.isBlank()) {
                    return absUrl;
                }
                return href;
            }
        }
        return fallbackUrl;
    }

    private String extractTitle(Document doc) {
        Elements titleElements = doc.getElementsByTag("title");
        for (Element titleEl : titleElements) {
            String titleText = titleEl.text().trim();
            if (!titleText.isBlank()) {
                return titleText;
            }
        }
        return null;
    }

    private String extractMetaDescription(Document doc) {
        Elements metaElements = doc.select("meta[name~=(?i)^description$]");
        for (Element metaEl : metaElements) {
            String content = metaEl.attr("content").trim();
            if (!content.isBlank()) {
                return content;
            }
        }
        return null;
    }

    private List<String> extractHeadings(Document doc) {
        Elements headingElements = doc.select("h1, h2, h3, h4, h5, h6");
        List<String> headings = new ArrayList<>();
        for (Element headingEl : headingElements) {
            String headingText = headingEl.text().trim();
            if (!headingText.isBlank()) {
                headings.add(headingText);
            }
        }
        return headings;
    }

    private String extractBodyText(Document doc) {
        Element body = doc.body();
        String text = body != null ? body.text() : doc.text();
        return text != null ? text.trim() : "";
    }

    private int calculateWordCount(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            return 0;
        }
        String[] tokens = bodyText.trim().split("\\s+");
        return tokens.length;
    }

    private String extractLanguage(Document doc) {
        Element htmlEl = doc.selectFirst("html");
        if (htmlEl != null && htmlEl.hasAttr("lang")) {
            String lang = htmlEl.attr("lang").trim();
            if (!lang.isBlank()) {
                return normalizeLanguage(lang);
            }
        }

        Element metaLang = doc.selectFirst("meta[http-equiv~=(?i)^content-language$]");
        if (metaLang != null && metaLang.hasAttr("content")) {
            String lang = metaLang.attr("content").trim();
            if (!lang.isBlank()) {
                return normalizeLanguage(lang);
            }
        }

        return null;
    }

    private String normalizeLanguage(String rawLang) {
        if (rawLang == null || rawLang.isBlank()) {
            return null;
        }
        String lang = rawLang.trim();
        if (lang.contains("-")) {
            String[] parts = lang.split("-", 2);
            return parts[0].toLowerCase() + "-" + parts[1].toUpperCase();
        } else if (lang.contains("_")) {
            String[] parts = lang.split("_", 2);
            return parts[0].toLowerCase() + "-" + parts[1].toUpperCase();
        } else {
            return lang.toLowerCase();
        }
    }

    public List<DiscoveredUrl> extractDiscoveredLinks(RawHtmlDocument rawHtmlDocument) {
        if (rawHtmlDocument == null || rawHtmlDocument.html() == null || rawHtmlDocument.html().isBlank()) {
            return Collections.emptyList();
        }
        String sourceUrl = rawHtmlDocument.finalUrl() != null && !rawHtmlDocument.finalUrl().isBlank()
                ? rawHtmlDocument.finalUrl()
                : rawHtmlDocument.url();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return Collections.emptyList();
        }

        Document doc;
        try {
            doc = Jsoup.parse(rawHtmlDocument.html(), sourceUrl);
        } catch (Exception e) {
            LOGGER.warn("HTML_PARSER_LINK_EXTRACTION_ERROR sourceUrl=\"{}\" error=\"{}\"", sourceUrl, e.getMessage());
            return Collections.emptyList();
        }

        return extractDiscoveredLinksFromDoc(doc, sourceUrl);
    }

    public List<DiscoveredUrl> extractDiscoveredLinksFromDoc(Document doc, String sourceUrl) {
        if (doc == null || sourceUrl == null || sourceUrl.isBlank()) {
            return Collections.emptyList();
        }

        Elements linkElements = doc.select("a[href]");
        Set<String> seenOnPage = new LinkedHashSet<>();
        List<DiscoveredUrl> discoveredUrls = new ArrayList<>();
        Instant now = Instant.now();

        for (Element linkEl : linkElements) {
            try {
                String rawHref = linkEl.attr("href").trim();
                if (rawHref.isBlank()) {
                    continue;
                }

                String lowerHref = rawHref.toLowerCase();
                if (lowerHref.startsWith("javascript:") || lowerHref.startsWith("mailto:")
                        || lowerHref.startsWith("tel:") || lowerHref.startsWith("data:")
                        || lowerHref.startsWith("#")) {
                    continue;
                }

                String absUrl = linkEl.absUrl("href").trim();
                if (absUrl.isBlank()) {
                    try {
                        URI baseUri = new URI(sourceUrl);
                        URI resolvedUri = baseUri.resolve(rawHref);
                        absUrl = resolvedUri.toASCIIString();
                    } catch (Exception ignored) {
                        continue;
                    }
                }

                if (absUrl.isBlank()) {
                    continue;
                }

                int fragmentIdx = absUrl.indexOf('#');
                if (fragmentIdx != -1) {
                    absUrl = absUrl.substring(0, fragmentIdx);
                }

                if (absUrl.isBlank()) {
                    continue;
                }

                URI parsedUri;
                try {
                    parsedUri = new URI(absUrl);
                } catch (URISyntaxException e) {
                    continue;
                }

                String scheme = parsedUri.getScheme();
                if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                    continue;
                }

                if (parsedUri.getHost() == null || parsedUri.getHost().isBlank()) {
                    continue;
                }

                if (seenOnPage.add(absUrl)) {
                    discoveredUrls.add(new DiscoveredUrl(absUrl, sourceUrl, now));
                }
            } catch (Exception e) {
                LOGGER.debug("HTML_PARSER_LINK_SKIPPED sourceUrl=\"{}\" error=\"{}\"", sourceUrl, e.getMessage());
            }
        }

        return discoveredUrls;
    }
}
