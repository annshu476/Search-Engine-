package com.searchengine.contentprocessor.parser;

import com.searchengine.contentprocessor.model.kafka.RawHtmlDocument;
import com.searchengine.contentprocessor.model.kafka.SearchDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlDocumentParserTest {

    private HtmlDocumentParser parser;

    @BeforeEach
    void setUp() {
        parser = new HtmlDocumentParser();
    }

    private RawHtmlDocument createSampleRawDoc(String html, String url, String finalUrl) {
        return new RawHtmlDocument(
                1,
                url,
                finalUrl,
                "hash12345",
                200,
                "text/html; charset=utf-8",
                html,
                Instant.parse("2026-08-13T10:00:00Z")
        );
    }

    @Test
    void test1_fullHtmlDocumentParsing() {
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <title>Full Document</title>
                    <meta name="description" content="A complete test document.">
                    <link rel="canonical" href="https://example.com/canonical-page">
                </head>
                <body>
                    <h1>Main Heading</h1>
                    <p>First paragraph text.</p>
                    <h2>Sub Heading</h2>
                    <p>Second paragraph text.</p>
                </body>
                </html>
                """;
        RawHtmlDocument raw = createSampleRawDoc(html, "https://example.com/page", "https://example.com/page");

        SearchDocument doc = parser.parse(raw);

        assertThat(doc).isNotNull();
        assertThat(doc.title()).isEqualTo("Full Document");
        assertThat(doc.metaDescription()).isEqualTo("A complete test document.");
        assertThat(doc.canonicalUrl()).isEqualTo("https://example.com/canonical-page");
        assertThat(doc.headings()).containsExactly("Main Heading", "Sub Heading");
        assertThat(doc.bodyText()).contains("Main Heading First paragraph text. Sub Heading Second paragraph text.");
        assertThat(doc.language()).isEqualTo("en");
        assertThat(doc.wordCount()).isGreaterThan(0);
    }

    @Test
    void test2_titleExtraction() {
        String html = "<html><head><title>  My Page Title  </title></head><body></body></html>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc.title()).isEqualTo("My Page Title");
    }

    @Test
    void test3_missingTitleReturnsNull() {
        String html = "<html><head></head><body><p>No title tag</p></body></html>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc.title()).isNull();
    }

    @Test
    void test4_metaDescriptionExtraction() {
        String html = "<html><head><meta name=\"description\" content=\"  Summary of the page  \"></head></html>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc.metaDescription()).isEqualTo("Summary of the page");
    }

    @Test
    void test5_missingMetaDescriptionReturnsNull() {
        String html = "<html><head><meta name=\"keywords\" content=\"test, search\"></head></html>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc.metaDescription()).isNull();
    }

    @Test
    void test6_multipleHeadingsInCorrectOrder() {
        String html = "<body><h1>H1 Title</h1><h3>H3 Sub</h3><h2>H2 Section</h2></body>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc.headings()).containsExactly("H1 Title", "H3 Sub", "H2 Section");
    }

    @Test
    void test7_emptyHeadingsIgnored() {
        String html = "<body><h1></h1><h2>  </h2><h3>Valid Heading</h3></body>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc.headings()).containsExactly("Valid Heading");
    }

    @Test
    void test8_bodyTextExtraction() {
        String html = "<body><p>Hello <span>world</span> from body.</p></body>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc.bodyText()).isEqualTo("Hello world from body.");
    }

    @Test
    void test9_scriptRemoval() {
        String html = "<body><h1>Title</h1><script>var secret = 'hidden';</script><p>Content</p></body>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc.bodyText()).isEqualTo("Title Content");
        assertThat(doc.bodyText()).doesNotContain("secret");
    }

    @Test
    void test10_styleRemoval() {
        String html = "<body><style>body { color: red; }</style><p>Styled text</p></body>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc.bodyText()).isEqualTo("Styled text");
        assertThat(doc.bodyText()).doesNotContain("color: red");
    }

    @Test
    void test11_noscriptRemoval() {
        String html = "<body><noscript><p>Please enable JavaScript</p></noscript><p>Main Content</p></body>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc.bodyText()).isEqualTo("Main Content");
        assertThat(doc.bodyText()).doesNotContain("enable JavaScript");
    }

    @Test
    void test12_templateRemoval() {
        String html = "<body><template><p>Template item</p></template><p>Active item</p></body>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc.bodyText()).isEqualTo("Active item");
        assertThat(doc.bodyText()).doesNotContain("Template item");
    }

    @Test
    void test13_whitespaceNormalization() {
        String html = "<body><p>   Hello      \n\n\t   world   </p></body>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc.bodyText()).isEqualTo("Hello world");
    }

    @Test
    void test14_wordCountCalculation() {
        String html = "<body><p>One two three four five.</p></body>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc.wordCount()).isEqualTo(5);
    }

    @Test
    void test15_canonicalAbsoluteUrl() {
        String html = "<html><head><link rel=\"canonical\" href=\"https://canonical.example.com/item\"></head></html>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "https://example.com/orig", "https://example.com/final"));
        assertThat(doc.canonicalUrl()).isEqualTo("https://canonical.example.com/item");
    }

    @Test
    void test16_canonicalRelativeUrl() {
        String html = "<html><head><link rel=\"canonical\" href=\"/sub/item\"></head></html>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "https://example.com/orig", "https://example.com/parent/page"));
        assertThat(doc.canonicalUrl()).isEqualTo("https://example.com/sub/item");
    }

    @Test
    void test17_missingCanonicalFallsBackToFinalUrl() {
        String html = "<html><head></head><body>No canonical</body></html>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "https://example.com/orig", "https://example.com/final"));
        assertThat(doc.canonicalUrl()).isEqualTo("https://example.com/final");
    }

    @Test
    void test18_htmlLangExtraction() {
        String html = "<html lang=\"en-US\"><head></head><body></body></html>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc.language()).isEqualTo("en-US");
    }

    @Test
    void test19_metaContentLanguageFallback() {
        String html = "<html><head><meta http-equiv=\"content-language\" content=\"EN-gb\"></head></html>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc.language()).isEqualTo("en-GB");
    }

    @Test
    void test20_missingLanguageReturnsNull() {
        String html = "<html><head></head><body></body></html>";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc.language()).isNull();
    }

    @Test
    void test21_malformedHtmlParsedGracefully() {
        String html = "<html><body><h1>Hello<p>World";
        SearchDocument doc = parser.parse(createSampleRawDoc(html, "http://a.com", "http://a.com"));
        assertThat(doc).isNotNull();
        assertThat(doc.headings()).isNotEmpty();
        assertThat(doc.bodyText()).contains("Hello World");
    }

    @Test
    void test22_emptyHtmlHandledSafely() {
        SearchDocument doc = parser.parse(createSampleRawDoc("", "http://a.com", "http://a.com/final"));
        assertThat(doc).isNotNull();
        assertThat(doc.title()).isNull();
        assertThat(doc.metaDescription()).isNull();
        assertThat(doc.headings()).isEmpty();
        assertThat(doc.bodyText()).isEqualTo("");
        assertThat(doc.wordCount()).isEqualTo(0);
        assertThat(doc.canonicalUrl()).isEqualTo("http://a.com/final");
    }

    @Test
    void test23_nullHtmlHandledSafely() {
        SearchDocument doc = parser.parse(createSampleRawDoc(null, "http://a.com", "http://a.com/final"));
        assertThat(doc).isNotNull();
        assertThat(doc.title()).isNull();
        assertThat(doc.metaDescription()).isNull();
        assertThat(doc.headings()).isEmpty();
        assertThat(doc.bodyText()).isEqualTo("");
        assertThat(doc.wordCount()).isEqualTo(0);
        assertThat(doc.canonicalUrl()).isEqualTo("http://a.com/final");
    }

    @Test
    void test24_metadataCopiedCorrectly() {
        Instant fetchedAt = Instant.parse("2026-08-13T11:00:00Z");
        RawHtmlDocument raw = new RawHtmlDocument(
                1,
                "https://orig.example.com",
                "https://final.example.com",
                "hash999",
                200,
                "text/html; charset=UTF-8",
                "<html><body><p>Text</p></body></html>",
                fetchedAt
        );

        SearchDocument doc = parser.parse(raw);

        assertThat(doc.url()).isEqualTo("https://orig.example.com");
        assertThat(doc.urlHash()).isEqualTo("hash999");
        assertThat(doc.statusCode()).isEqualTo(200);
        assertThat(doc.contentType()).isEqualTo("text/html; charset=UTF-8");
        assertThat(doc.fetchedAt()).isEqualTo(fetchedAt);
    }

    @Test
    void test25_indexedAtIsPopulated() {
        Instant before = Instant.now().minusSeconds(1);
        SearchDocument doc = parser.parse(createSampleRawDoc("<html><body>Hi</body></html>", "http://a.com", "http://a.com"));
        Instant after = Instant.now().plusSeconds(1);

        assertThat(doc.indexedAt()).isNotNull();
        assertThat(doc.indexedAt()).isBetween(before, after);
    }

    @Test
    void test26_nullRawHtmlDocumentReturnsNull() {
        assertThat(parser.parse(null)).isNull();
    }

    @Test
    void test27_extractDiscoveredLinks_allRequirements() {
        String html = """
                <html>
                <body>
                    <a href="https://example.com/about">Absolute Link</a>
                    <a href="/about">Relative Link</a>
                    <a href="../products">Parent Relative Link</a>
                    <a href="https://example.com/page#section">Fragment Link</a>
                    <a href="javascript:void(0)">JS Link</a>
                    <a href="mailto:test@example.com">Mailto Link</a>
                    <a href="tel:+123456789">Tel Link</a>
                    <a href="data:text/html;base64,123">Data Link</a>
                    <a href="http://[invalid-host]:80">Invalid Host Link</a>
                    <a href="https://external.org/deep/link">External Link</a>
                    <a href="">Empty Link</a>
                    <a href="https://example.com/about">Duplicate Link</a>
                    <a href="ht%20tp://broken">Broken Link</a>
                </body>
                </html>
                """;

        RawHtmlDocument raw = createSampleRawDoc(html, "https://example.com/category/sub", "https://example.com/category/sub");
        List<com.searchengine.contentprocessor.model.kafka.DiscoveredUrl> links = parser.extractDiscoveredLinks(raw);

        List<String> urls = links.stream().map(com.searchengine.contentprocessor.model.kafka.DiscoveredUrl::url).toList();

        assertThat(urls).contains(
                "https://example.com/about",
                "https://example.com/products",
                "https://example.com/page",
                "https://external.org/deep/link"
        );

        // Check non-web & malformed links are excluded
        assertThat(urls).noneMatch(u -> u.contains("javascript:") || u.contains("mailto:") || u.contains("tel:") || u.contains("data:"));
        assertThat(urls).noneMatch(u -> u.contains("#section"));
        assertThat(urls).noneMatch(u -> u.contains("[invalid-host]"));

        // Check page-level deduplication (https://example.com/about appears only once)
        long countAbout = urls.stream().filter(u -> u.equals("https://example.com/about")).count();
        assertThat(countAbout).isEqualTo(1);
    }
}
