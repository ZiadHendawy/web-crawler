package com.webcrawler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LinkParserTest {

    private LinkParser parser;

    @BeforeEach
    void setUp() {
        parser = new LinkParser();
    }

    @Test
    void extractsAbsoluteLinks() {
        String html = "<html><body><a href='https://example.com/page'>link</a></body></html>";
        List<String> links = parser.extractLinks("https://example.com/", html);
        assertEquals(List.of("https://example.com/page"), links);
    }

    @Test
    void resolvesRelativeLinks() {
        String html = "<html><body><a href='/about'>About</a></body></html>";
        List<String> links = parser.extractLinks("https://example.com/", html);
        assertEquals(List.of("https://example.com/about"), links);
    }

    @Test
    void excludesNonHttpLinks() {
        String html = "<html><body>" +
                "<a href='mailto:a@b.com'>email</a>" +
                "<a href='javascript:void(0)'>js</a>" +
                "<a href='/page'>valid</a>" +
                "</body></html>";
        List<String> links = parser.extractLinks("https://example.com/", html);
        assertEquals(List.of("https://example.com/page"), links);
    }

    @Test
    void deduplicatesLinks() {
        String html = "<html><body>" +
                "<a href='/page'>one</a>" +
                "<a href='/page'>two</a>" +
                "</body></html>";
        List<String> links = parser.extractLinks("https://example.com/", html);
        assertEquals(1, links.size());
    }

    @Test
    void returnsEmptyListForBlankHtml() {
        assertTrue(parser.extractLinks("https://example.com/", "").isEmpty());
        assertTrue(parser.extractLinks("https://example.com/", null).isEmpty());
    }
}
