package com.ruleforge.console.storage;

import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Normalizes XML content before writing to Git so that:
 * <ol>
 *   <li>Diffs are stable (same logical content → same byte output)</li>
 *   <li>Cell/script-cell elements are sorted by row, col</li>
 *   <li>Attributes are sorted alphabetically within each element</li>
 *   <li>Indentation is consistent (4 spaces)</li>
 * </ol>
 */
@Slf4j
@Component
public class XmlCanonicalizer {

    private static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"utf-8\"?>";
    private static final String INDENT = "    "; // 4 spaces

    /**
     * Canonicalize an XML string.
     * Returns the normalized form, or the original string if parsing fails.
     */
    public String canonicalize(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return xml;
        }
        try {
            Document doc = DocumentHelper.parseText(xml);
            Element root = doc.getRootElement();
            sortAttributesRecursive(root);
            sortCellElements(root);
            return formatDocument(doc);
        } catch (Exception e) {
            log.warn("Failed to canonicalize XML, returning original: {}", e.getMessage());
            return xml;
        }
    }

    /**
     * Recursively sort attributes alphabetically on all elements.
     */
    @SuppressWarnings("unchecked")
    private void sortAttributesRecursive(Element element) {
        // dom4j attributes are stored in a list; re-adding in sorted order normalizes them
        List<org.dom4j.Attribute> attrs = new ArrayList<>(element.attributes());
        if (attrs.size() > 1) {
            attrs.sort(Comparator.comparing(org.dom4j.Attribute::getName));
            element.setAttributes(attrs);
        }
        for (Element child : (List<Element>) element.elements()) {
            sortAttributesRecursive(child);
        }
    }

    /**
     * Sort cell and script-cell elements by their row and col attributes.
     * Only applies to decision-table, script-decision-table, complex-scorecard, crosstab.
     */
    @SuppressWarnings("unchecked")
    private void sortCellElements(Element root) {
        String rootName = root.getName();
        if ("decision-table".equals(rootName) || "script-decision-table".equals(rootName)
                || "complex-scorecard".equals(rootName)) {
            sortCellChildren(root, "cell", "script-cell");
        } else if ("crosstab".equals(rootName)) {
            sortCellChildren(root, "condition-cell", "value-cell");
        }
    }

    private void sortCellChildren(Element root, String... cellTags) {
        List<Element> cells = new ArrayList<>();
        List<Element> nonCells = new ArrayList<>();

        for (Object obj : root.content()) {
            if (obj instanceof Element el) {
                boolean isCell = false;
                for (String tag : cellTags) {
                    if (tag.equals(el.getName())) {
                        isCell = true;
                        break;
                    }
                }
                if (isCell) {
                    cells.add(el);
                } else {
                    nonCells.add(el);
                }
            }
        }

        if (cells.isEmpty()) {
            return;
        }

        // Sort by row then col
        cells.sort(Comparator
                .comparingInt((Element e) -> intAttr(e, "row", 0))
                .thenComparingInt(e -> intAttr(e, "col", 0)));

        // Remove all content and re-add: non-cells first (row/col definitions), then sorted cells
        root.clearContent();
        // Re-add non-cell elements (like row, col definitions) first
        for (Element el : nonCells) {
            root.add(el.detach());
        }
        // Then sorted cells
        for (Element el : cells) {
            root.add(el.detach());
        }
    }

    private int intAttr(Element e, String attrName, int defaultValue) {
        String val = e.attributeValue(attrName);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    /**
     * Format a dom4j Document with consistent indentation.
     * Uses trimText + padText but strips trailing whitespace per line
     * to avoid dom4j's padding artifacts.
     */
    private String formatDocument(Document doc) throws Exception {
        OutputFormat format = new OutputFormat();
        format.setIndent(INDENT);
        format.setNewlines(true);
        format.setNewLineAfterDeclaration(false);
        format.setPadText(false);
        format.setTrimText(true);
        // Don't include encoding in declaration — we add our own
        format.setSuppressDeclaration(true);

        StringWriter writer = new StringWriter();
        XMLWriter xmlWriter = new XMLWriter(writer, format);
        xmlWriter.write(doc);
        xmlWriter.close();

        // Strip trailing whitespace from each line
        String formatted = writer.toString().lines()
                .map(String::stripTrailing)
                .collect(java.util.stream.Collectors.joining("\n"))
                .trim();

        // Prepend consistent declaration
        return XML_DECLARATION + "\n" + formatted + "\n";
    }
}
