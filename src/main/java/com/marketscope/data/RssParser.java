package com.marketscope.data;

import com.marketscope.model.NewsItem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads RSS 2.0 items (title, link, date, summary). The parser refuses DTDs and external
 * entities, so a hostile feed cannot make it read local files or other URLs.
 */
public final class RssParser {

    /** A feed item before sentiment is scored. */
    public record Item(String title, String link, Instant published, String summary) {
    }

    private RssParser() {
    }

    public static List<Item> parse(String xml) throws IOException {
        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            document = builder.parse(new ByteArrayInputStream(xml.strip().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IOException("The news feed could not be read.", e);
        }
        List<Item> items = new ArrayList<>();
        NodeList nodes = document.getElementsByTagName("item");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element item = (Element) nodes.item(i);
            String title = text(item, "title");
            if (title == null || title.isBlank()) {
                continue;
            }
            items.add(new Item(clean(title), text(item, "link"), date(text(item, "pubDate")),
                    clean(text(item, "description"))));
        }
        return items;
    }

    private static String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() == 0 ? null : list.item(0).getTextContent();
    }

    private static final List<DateTimeFormatter> LENIENT = List.of(
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm[:ss] Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm[:ss] z", Locale.ENGLISH));

    /**
     * RFC 1123 dates ("Wed, 14 Oct 2026 08:41:00 +0000" or "... GMT"); null when unreadable.
     * Feeds sometimes get the weekday wrong, which the strict parser rejects, so the weekday is
     * ignored when it does not match.
     */
    static Instant date(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.strip();
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            String withoutDay = value.replaceFirst("^[A-Za-z]{3},\\s*", "");
            for (DateTimeFormatter format : LENIENT) {
                try {
                    return ZonedDateTime.parse(withoutDay, format).toInstant();
                } catch (DateTimeParseException ignored) {
                    // try the next form
                }
            }
            return null;
        }
    }

    /** Strips HTML tags and entities a summary may carry, and squeezes whitespace. */
    static String clean(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("<[^>]*>", " ")
                .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
                .replaceAll("\\s+", " ").strip();
    }
}
