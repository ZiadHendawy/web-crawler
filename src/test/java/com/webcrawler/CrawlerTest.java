package com.webcrawler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrawlerTest {

    @Mock
    private Fetcher fetcher;

    private ExecutorService executor;
    private Crawler crawler;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(4);
        crawler = new Crawler(fetcher, new LinkParser(), executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void crawlsAllPagesOnSameDomain() throws Exception {
        when(fetcher.fetch("https://example.com/")).thenReturn(
                "<html><body><a href='/page1'>p1</a></body></html>");
        when(fetcher.fetch("https://example.com/page1")).thenReturn(
                "<html><body><a href='/page2'>p2</a></body></html>");
        when(fetcher.fetch("https://example.com/page2")).thenReturn(
                "<html><body></body></html>");

        List<PageResult> results = new ArrayList<>();
        crawler.crawl("https://example.com/", results::add);

        assertEquals(3, results.size());
        List<String> urls = results.stream().map(PageResult::url).toList();
        assertTrue(urls.contains("https://example.com/"));
        assertTrue(urls.contains("https://example.com/page1"));
        assertTrue(urls.contains("https://example.com/page2"));
    }

    @Test
    void doesNotFollowExternalLinks() throws Exception {
        when(fetcher.fetch("https://example.com/")).thenReturn(
                "<html><body>" +
                "<a href='/internal'>internal</a>" +
                "<a href='https://other.com/page'>external</a>" +
                "</body></html>");
        when(fetcher.fetch("https://example.com/internal")).thenReturn(
                "<html><body></body></html>");

        List<PageResult> results = new ArrayList<>();
        crawler.crawl("https://example.com/", results::add);

        assertEquals(2, results.size());
        verify(fetcher, never()).fetch("https://other.com/page");
    }

    @Test
    void doesNotVisitSamePageTwice() throws Exception {
        when(fetcher.fetch("https://example.com/")).thenReturn(
                "<html><body><a href='/page1'>p1</a></body></html>");
        when(fetcher.fetch("https://example.com/page1")).thenReturn(
                "<html><body><a href='/'>home</a></body></html>");

        List<PageResult> results = new ArrayList<>();
        crawler.crawl("https://example.com/", results::add);

        assertEquals(2, results.size());
        verify(fetcher, times(1)).fetch("https://example.com/");
        verify(fetcher, times(1)).fetch("https://example.com/page1");
    }

    @Test
    void handlesCyclicLinksWithoutInfiniteLoop() throws Exception {
        when(fetcher.fetch("https://example.com/")).thenReturn(
                "<html><body><a href='/a'>a</a></body></html>");
        when(fetcher.fetch("https://example.com/a")).thenReturn(
                "<html><body><a href='/b'>b</a></body></html>");
        when(fetcher.fetch("https://example.com/b")).thenReturn(
                "<html><body><a href='/a'>back to a</a></body></html>");

        List<PageResult> results = new ArrayList<>();
        crawler.crawl("https://example.com/", results::add);

        assertEquals(3, results.size());
    }

    @Test
    void handlesFailedFetchGracefully() throws Exception {
        when(fetcher.fetch("https://example.com/")).thenReturn(
                "<html><body><a href='/broken'>broken</a></body></html>");
        when(fetcher.fetch("https://example.com/broken")).thenThrow(new IOException("timeout"));

        List<PageResult> results = new ArrayList<>();
        assertDoesNotThrow(() -> crawler.crawl("https://example.com/", results::add));
        assertEquals(2, results.size());
    }

    @Test
    void throwsOnInvalidStartUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> crawler.crawl("not-a-url", result -> {}));
    }

    @Test
    void deduplicatesUrlsWithFragments() throws Exception {
        when(fetcher.fetch("https://example.com/")).thenReturn(
                "<html><body>" +
                "<a href='/page'>link</a>" +
                "<a href='/page#section'>same page, with fragment</a>" +
                "</body></html>");
        when(fetcher.fetch("https://example.com/page")).thenReturn(
                "<html><body></body></html>");

        List<PageResult> results = new ArrayList<>();
        crawler.crawl("https://example.com/", results::add);

        assertEquals(2, results.size());
        verify(fetcher, times(1)).fetch("https://example.com/page");
    }

    @Test
    void doesNotFollowLinksOnDifferentSubdomain() throws Exception {
        when(fetcher.fetch("https://crawlme.example.com/")).thenReturn(
                "<html><body>" +
                "<a href='/page'>same</a>" +
                "<a href='https://example.com/about'>parent domain</a>" +
                "<a href='https://community.example.com/'>sibling subdomain</a>" +
                "</body></html>");
        when(fetcher.fetch("https://crawlme.example.com/page")).thenReturn(
                "<html><body></body></html>");

        List<PageResult> results = new ArrayList<>();
        crawler.crawl("https://crawlme.example.com/", results::add);

        assertEquals(2, results.size());
        verify(fetcher, never()).fetch("https://example.com/about");
        verify(fetcher, never()).fetch("https://community.example.com/");
    }
}
