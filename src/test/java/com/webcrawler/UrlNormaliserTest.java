package com.webcrawler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlNormaliserTest {

    @Test
    void removesFragment() {
        assertEquals("https://example.com/page",
                UrlNormaliser.normalise("https://example.com/page#section"));
    }

    @Test
    void removesTrailingSlashOnNonRootPath() {
        assertEquals("https://example.com/page",
                UrlNormaliser.normalise("https://example.com/page/"));
    }

    @Test
    void preservesRootPath() {
        assertEquals("https://example.com/",
                UrlNormaliser.normalise("https://example.com/"));
    }

    @Test
    void preservesQueryParams() {
        assertEquals("https://example.com/search?q=hello",
                UrlNormaliser.normalise("https://example.com/search?q=hello"));
    }

    @Test
    void isSameHostMatchesExactSubdomain() {
        assertTrue(UrlNormaliser.isSameHost("https://crawlme.example.com/page", "crawlme.example.com"));
        assertFalse(UrlNormaliser.isSameHost("https://example.com/page", "crawlme.example.com"));
        assertFalse(UrlNormaliser.isSameHost("https://community.example.com/page", "crawlme.example.com"));
    }

    @Test
    void isSameHostIsCaseInsensitive() {
        assertTrue(UrlNormaliser.isSameHost("https://EXAMPLE.COM/page", "example.com"));
    }
}
