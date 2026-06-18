package com.webcrawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;
import java.util.stream.Collectors;

public class LinkParser {

    public List<String> extractLinks(String baseUrl, String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }

        Document doc = Jsoup.parse(html, baseUrl);
        return doc.select("a[href]").stream()
                .map(el -> el.absUrl("href"))
                .filter(href -> !href.isBlank())
                .filter(href -> href.startsWith("http://") || href.startsWith("https://"))
                .distinct()
                .collect(Collectors.toList());
    }
}
