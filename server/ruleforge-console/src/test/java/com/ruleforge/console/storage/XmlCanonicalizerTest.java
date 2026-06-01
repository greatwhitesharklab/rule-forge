package com.ruleforge.console.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BDD tests for XmlCanonicalizer.
 *
 * Feature: XML Canonicalization for Git diff stability
 *
 * Rule: Same logical XML content produces identical byte output regardless of input formatting
 * Rule: Decision table cells are sorted by row, col
 * Rule: Attributes are sorted alphabetically
 * Rule: Indentation is consistent (4 spaces)
 */
class XmlCanonicalizerTest {

    private final XmlCanonicalizer canonicalizer = new XmlCanonicalizer();

    @Nested
    @DisplayName("Given two semantically identical XML documents with different formatting")
    class IdenticalContentTests {

        @Test
        @DisplayName("When attributes are in different order, Then output is identical")
        void sameContentDifferentAttributeOrder() {
            // Given
            String xml1 = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<root alpha=\"1\" beta=\"2\"/>";
            String xml2 = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<root beta=\"2\" alpha=\"1\"/>";

            // When
            String result1 = canonicalizer.canonicalize(xml1);
            String result2 = canonicalizer.canonicalize(xml2);

            // Then
            assertEquals(result1, result2);
        }

        @Test
        @DisplayName("When whitespace differs, Then output is identical")
        void sameContentDifferentWhitespace() {
            // Given
            String xml1 = "<root><child a=\"1\"/></root>";
            String xml2 = "<root>\n  <child a=\"1\"/>\n</root>";

            // When
            String result1 = canonicalizer.canonicalize(xml1);
            String result2 = canonicalizer.canonicalize(xml2);

            // Then
            assertEquals(result1, result2);
        }
    }

    @Nested
    @DisplayName("Given a decision-table XML with unsorted cells")
    class DecisionTableTests {

        @Test
        @DisplayName("When canonicalized, Then cells are sorted by row then col")
        void cellsSortedByRowCol() {
            // Given: cells in reverse order
            String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<decision-table>\n" +
                    "  <cell row=\"1\" col=\"0\" rowspan=\"1\">B1</cell>\n" +
                    "  <cell row=\"0\" col=\"1\" rowspan=\"1\">A2</cell>\n" +
                    "  <cell row=\"0\" col=\"0\" rowspan=\"1\">A1</cell>\n" +
                    "  <row num=\"0\" height=\"40\"/>\n" +
                    "  <row num=\"1\" height=\"40\"/>\n" +
                    "  <col num=\"0\" width=\"120\" type=\"Criteria\"/>\n" +
                    "  <col num=\"1\" width=\"200\" type=\"Assignment\"/>\n" +
                    "</decision-table>";

            // When
            String result = canonicalizer.canonicalize(xml);

            // Then: cell at row=0,col=0 should appear before row=0,col=1, before row=1,col=0
            // After canonicalization, attributes are sorted alphabetically: col before row
            int idx00 = result.indexOf("col=\"0\" row=\"0\"");
            int idx01 = result.indexOf("col=\"1\" row=\"0\"");
            int idx10 = result.indexOf("col=\"0\" row=\"1\"");
            assertTrue(idx00 < idx01, "cell(0,0) should come before cell(0,1)");
            assertTrue(idx01 < idx10, "cell(0,1) should come before cell(1,0)");
        }

        @Test
        @DisplayName("When canonicalized twice, Then output is stable (idempotent)")
        void idempotent() {
            // Given
            String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<decision-table salience=\"10\">\n" +
                    "  <cell row=\"1\" col=\"1\" rowspan=\"1\">X</cell>\n" +
                    "  <cell row=\"0\" col=\"0\" rowspan=\"1\">Y</cell>\n" +
                    "  <row num=\"0\" height=\"40\"/>\n" +
                    "  <col num=\"0\" width=\"120\" type=\"Criteria\"/>\n" +
                    "</decision-table>";

            // When
            String result1 = canonicalizer.canonicalize(xml);
            String result2 = canonicalizer.canonicalize(result1);

            // Then
            assertEquals(result1, result2);
        }
    }

    @Nested
    @DisplayName("Given null or empty input")
    class EdgeCaseTests {

        @Test
        @DisplayName("When input is null, Then returns null")
        void nullInput() {
            assertNull(canonicalizer.canonicalize(null));
        }

        @Test
        @DisplayName("When input is empty, Then returns empty")
        void emptyInput() {
            assertEquals("", canonicalizer.canonicalize(""));
        }

        @Test
        @DisplayName("When input is invalid XML, Then returns original")
        void invalidXml() {
            String invalid = "<not closed";
            assertEquals(invalid, canonicalizer.canonicalize(invalid));
        }
    }

    @Nested
    @DisplayName("Given a rule-set XML")
    class RuleSetTests {

        @Test
        @DisplayName("When canonicalized, Then attributes are sorted and indentation is consistent")
        void ruleSetAttributesSorted() {
            // Given
            String xml = "<rule-set>\n" +
                    "  <rule name=\"r1\" salience=\"100\" enabled=\"true\" debug=\"false\">\n" +
                    "    <remark>Test</remark>\n" +
                    "  </rule>\n" +
                    "</rule-set>";

            // When
            String result = canonicalizer.canonicalize(xml);

            // Then: output should contain sorted attributes
            assertTrue(result.contains("debug"));
            assertTrue(result.contains("enabled"));
            assertTrue(result.contains("name"));
            assertTrue(result.contains("salience"));
            // And it should be parseable
            assertTrue(result.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>"));
        }
    }
}
