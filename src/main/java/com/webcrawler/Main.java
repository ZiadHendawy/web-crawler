package com.webcrawler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    // 2x cores: fetching is I/O-bound so more threads than cores is correct.
    // Cap at 16: politeness — too many simultaneous connections risks rate-limiting.
    private static final int DEFAULT_THREADS = Math.min(Runtime.getRuntime().availableProcessors() * 2, 16);
    private static final String DEFAULT_URL = "https://example.com/";

    public static void main(String[] args) throws InterruptedException {
        String startUrl = args.length > 0 ? args[0] : DEFAULT_URL;
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_THREADS;

        System.out.printf("Crawling %s with %d threads%n%n", startUrl, threads);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        Crawler crawler = new Crawler(new HttpFetcher(), new LinkParser(), executor);

        try {
            crawler.crawl(startUrl, Main::printResult);
        } finally {
            executor.shutdown();
        }
    }

    private static void printResult(PageResult result) {
        System.out.println(result.url());
        result.links().forEach(link -> System.out.println("  " + link));
        System.out.println();
    }
}
