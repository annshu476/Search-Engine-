package com.searchengine.indexer.search;

import com.searchengine.indexer.exception.SearchQuerySyntaxException;
import com.searchengine.indexer.model.search.ParsedSearchQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchQueryParserTest {

    private SearchQueryParser parser;

    @BeforeEach
    void setUp() {
        parser = new SearchQueryParser();
    }

    @Test
    void parse_plainSingleTerm_returnsNormalTerm() {
        ParsedSearchQuery parsed = parser.parse("spring");

        assertThat(parsed.normalTerms()).containsExactly("spring");
        assertThat(parsed.exactPhrases()).isEmpty();
        assertThat(parsed.requiredTerms()).isEmpty();
        assertThat(parsed.excludedTerms()).isEmpty();
    }

    @Test
    void parse_plainMultiTerm_returnsNormalTerms() {
        ParsedSearchQuery parsed = parser.parse("spring boot");

        assertThat(parsed.normalTerms()).containsExactly("spring", "boot");
        assertThat(parsed.exactPhrases()).isEmpty();
    }

    @Test
    void parse_exactPhrase_returnsExactPhrase() {
        ParsedSearchQuery parsed = parser.parse("\"spring boot\"");

        assertThat(parsed.exactPhrases()).containsExactly("spring boot");
        assertThat(parsed.normalTerms()).isEmpty();
    }

    @Test
    void parse_requiredTerm_returnsRequiredTerm() {
        ParsedSearchQuery parsed = parser.parse("+spring");

        assertThat(parsed.requiredTerms()).containsExactly("spring");
        assertThat(parsed.normalTerms()).isEmpty();
    }

    @Test
    void parse_excludedTerm_throwsOrParsesCorrectly() {
        // Excluded term alone has no positive terms, so syntax error is thrown
        assertThatThrownBy(() -> parser.parse("-xml"))
                .isInstanceOf(SearchQuerySyntaxException.class)
                .hasMessageContaining("Invalid search query syntax");
    }

    @Test
    void parse_multipleRequiredTerms_returnsRequiredTerms() {
        ParsedSearchQuery parsed = parser.parse("+spring +boot");

        assertThat(parsed.requiredTerms()).containsExactly("spring", "boot");
    }

    @Test
    void parse_multipleExcludedTerms_withPositiveTerm_returnsExcludedTerms() {
        ParsedSearchQuery parsed = parser.parse("spring -xml -json");

        assertThat(parsed.normalTerms()).containsExactly("spring");
        assertThat(parsed.excludedTerms()).containsExactly("xml", "json");
    }

    @Test
    void parse_mixedNormalPhraseRequiredExcluded_returnsAllComponents() {
        ParsedSearchQuery parsed = parser.parse("spring \"boot framework\" +java -xml");

        assertThat(parsed.normalTerms()).containsExactly("spring");
        assertThat(parsed.exactPhrases()).containsExactly("boot framework");
        assertThat(parsed.requiredTerms()).containsExactly("java");
        assertThat(parsed.excludedTerms()).containsExactly("xml");
    }

    @Test
    void parse_multiplePhrases_returnsMultiplePhrases() {
        ParsedSearchQuery parsed = parser.parse("\"spring boot\" \"microservices architecture\"");

        assertThat(parsed.exactPhrases()).containsExactly("spring boot", "microservices architecture");
    }

    @Test
    void parse_emptyQuery_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search query must not be blank");
    }

    @Test
    void parse_whitespaceOnlyQuery_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search query must not be blank");
    }

    @Test
    void parse_unclosedQuote_throwsSearchQuerySyntaxException() {
        assertThatThrownBy(() -> parser.parse("\"unclosed phrase"))
                .isInstanceOf(SearchQuerySyntaxException.class)
                .hasMessageContaining("Invalid search query syntax");
    }

    @Test
    void parse_standalonePlus_throwsSearchQuerySyntaxException() {
        assertThatThrownBy(() -> parser.parse("+"))
                .isInstanceOf(SearchQuerySyntaxException.class)
                .hasMessageContaining("Invalid search query syntax");
    }

    @Test
    void parse_standaloneMinus_throwsSearchQuerySyntaxException() {
        assertThatThrownBy(() -> parser.parse("-"))
                .isInstanceOf(SearchQuerySyntaxException.class)
                .hasMessageContaining("Invalid search query syntax");
    }

    @Test
    void parse_operatorFollowedByWhitespace_throwsSearchQuerySyntaxException() {
        assertThatThrownBy(() -> parser.parse("+ \"spring boot\""))
                .isInstanceOf(SearchQuerySyntaxException.class)
                .hasMessageContaining("Invalid search query syntax");
    }

    @Test
    void parse_quotedEmptyPhrase_throwsSearchQuerySyntaxException() {
        assertThatThrownBy(() -> parser.parse("\"\""))
                .isInstanceOf(SearchQuerySyntaxException.class)
                .hasMessageContaining("Invalid search query syntax");
    }

    @Test
    void parse_punctuationInsideNormalTerm_parsesTermWithPunctuation() {
        ParsedSearchQuery parsed = parser.parse("c++ v1.0");

        assertThat(parsed.normalTerms()).containsExactly("c++", "v1.0");
    }

    @Test
    void parse_punctuationInsidePhrase_parsesPhraseWithPunctuation() {
        ParsedSearchQuery parsed = parser.parse("\"spring-boot: 3.5.0!\"");

        assertThat(parsed.exactPhrases()).containsExactly("spring-boot: 3.5.0!");
    }

    @Test
    void parse_queryContainingUrl_parsesUrlAsTerm() {
        ParsedSearchQuery parsed = parser.parse("https://searchengine.org/page1");

        assertThat(parsed.normalTerms()).containsExactly("https://searchengine.org/page1");
    }

    @Test
    void parse_queryWithRepeatedOperators_parsesTermsAndOperators() {
        ParsedSearchQuery parsed = parser.parse("spring +boot +java -xml -json");

        assertThat(parsed.normalTerms()).containsExactly("spring");
        assertThat(parsed.requiredTerms()).containsExactly("boot", "java");
        assertThat(parsed.excludedTerms()).containsExactly("xml", "json");
    }
}
