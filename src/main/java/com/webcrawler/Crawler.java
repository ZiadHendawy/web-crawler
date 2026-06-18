package com.webcrawler;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Crawler {

    private final Fetcher fetcher;
    private final LinkParser parser;
    private final ExecutorService executor;

    public Crawler(Fetcher fetcher, LinkParser parser, ExecutorService executor) {
        this.fetcher = fetcher;
        this.parser = parser;
        this.executor = executor;
    }

    /**
     * Crawls all pages reachable from startUrl on the same subdomain.
     *
     * Uses BFS with batch-level parallelism: all URLs at the current frontier
     * are fetched concurrently via the executor, then newly discovered URLs
     * form the next frontier. This bounds memory to O(pages) and avoids
     * unbounded task submission while keeping threads busy.
     *
     * @param startUrl  seed URL; only pages on the same host are followed
     * @param onResult  called once per page as each BFS level completes
     */
    public void crawl(String startUrl, Consumer<PageResult> onResult) throws InterruptedException {
        String seedHost = extractHost(startUrl);
        if (seedHost == null) {
            throw new IllegalArgumentException("Cannot extract host from URL: " + startUrl);
        }

        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();

        String normStart = UrlNormaliser.normalise(startUrl);
        queue.add(normStart);
        visited.add(normStart);

        while (!queue.isEmpty()) {
            List<String> batch = new ArrayList<>(queue);
            queue.clear();

            List<Callable<PageResult>> tasks = batch.stream()
                    .map(url -> (Callable<PageResult>) () -> fetchPage(url))
                    .collect(Collectors.toList());

            List<Future<PageResult>> futures = executor.invokeAll(tasks);

            // invokeAll guarantees all futures are done before returning, so future.get() is non-blocking.
            // ExecutionException is unreachable: fetchPage catches all exceptions internally.
            for (Future<PageResult> future : futures) {
                try {
                    PageResult result = future.get();
                    onResult.accept(result);

                    for (String link : result.links()) {
                        String normLink = UrlNormaliser.normalise(link);
                        if (UrlNormaliser.isSameHost(normLink, seedHost) && visited.add(normLink)) {
                            queue.add(normLink);
                        }
                    }
                } catch (ExecutionException e) {
                    throw new IllegalStateException("Unexpected crawler task failure", e);
                }
            }
        }
    }

    private PageResult fetchPage(String url) {
        try {
            String html = fetcher.fetch(url);
            List<String> links = parser.extractLinks(url, html);
            return new PageResult(url, links);
        } catch (Exception e) {
            System.err.println("Error fetching " + url + ": " + e.getMessage());
            return new PageResult(url, List.of());
        }
    }

    private static String extractHost(String url) {
        try {
            return new URI(url).getHost();
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
