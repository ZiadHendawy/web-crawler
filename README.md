# Web Crawler

A concurrent web crawler written in Java. Given a starting URL it visits every page on the same subdomain, printing each URL and the links found on it.

## How to run

**Prerequisites:** Java 17+, Maven 3.8+

```bash
# Run tests (17 tests covering BFS traversal, deduplication, domain scoping, and error handling)
mvn test

# Run the crawler (defaults to https://example.com/)
mvn compile exec:java

# Run against a different URL
mvn compile exec:java -Dexec.args="https://example.com/"

# Run with a custom thread count (default: 2 × CPU cores, max 16)
mvn compile exec:java -Dexec.args="https://example.com/ 8"
```

**Output format:** for each page visited, the URL is printed followed by all links found on that page (both internal and external).

```
https://example.com/
  https://example.com/about
  https://example.com/blog
  https://external.com/page

https://example.com/about
  https://example.com/
  ...
```

## Design

### Language and build tool

Java was chosen because its concurrency primitives (`ExecutorService`, `invokeAll`, `Future`) express the parallelism model directly in the code — a reviewer can see exactly how concurrency is bounded and controlled without needing a separate explanation. Go and Python were also considered; Java made the concurrency model more explicit and readable.

Maven was chosen over Gradle as the build tool — it is the most familiar choice in Java backend engineering and requires no setup beyond `mvn test`.

### Structure

| Class | Responsibility |
|---|---|
| `Crawler` | BFS orchestration and concurrency |
| `HttpFetcher` | HTTP requests via `java.net.http.HttpClient` |
| `LinkParser` | Extracts and resolves links from HTML via Jsoup |
| `UrlNormaliser` | Strips fragments, normalises trailing slashes |
| `PageResult` | Immutable record: URL + links found |
| `Fetcher` | Interface enabling test doubles for `HttpFetcher` |

### Concurrency model

The crawler uses **BFS with batch-level parallelism**: all URLs at the current frontier are submitted to a fixed thread pool via `ExecutorService.invokeAll()`, which blocks until the entire batch completes. Newly discovered URLs form the next frontier.

I chose this over continuous task submission (e.g. recursive `CompletableFuture`) because termination is trivial — the loop exits when the queue is empty — and the code is straightforward to reason about. The downside is that threads can sit idle at the tail of a large batch while a few slow pages finish; a work-stealing approach (e.g. `ForkJoinPool`) would improve utilisation at the cost of more complex termination logic.

The visited set is a plain `HashSet` accessed only from the main thread (results are collected synchronously after `invokeAll`), so no locking is needed.

### Thread pool sizing

The default thread count is `2 × CPU cores`, capped at 16. Fetching is I/O-bound — threads spend most of their time waiting on network responses, so more threads than cores improves throughput. The cap at 16 is a politeness constraint: too many simultaneous connections to one server risks rate-limiting. In production both values would be configurable and tuned against the target server's `robots.txt` `Crawl-delay`.

### URL normalisation

Before a URL enters the visited set or the queue it is normalised:
- Fragment stripped (`/page#section` → `/page`): anchors are not separate pages
- Trailing slash removed on non-root paths (`/page/` → `/page`): prevents duplicate visits

Query parameters are preserved because they may represent distinct pages.

### Subdomain scoping

Only URLs whose host exactly matches the seed URL's host are followed. `crawlme.example.com` will not follow links to `example.com` or `community.example.com`.

### Error handling

A failed fetch (network error, timeout) returns a `PageResult` with an empty link list. The error is logged to stderr and the crawl continues. A non-2xx HTTP response is treated the same way — the URL is marked visited so it is not retried.

### Libraries used

- **Jsoup 1.22.2** for HTML parsing and relative URL resolution. Writing a correct HTML parser is out of scope and would be reinventing the wheel; the task explicitly permits HTML parsing libraries.
- **java.net.http.HttpClient** (JDK built-in since Java 11) for HTTP. No external HTTP library needed.

---

## Known limitations

These are gaps in the current implementation worth improving.

**Robots.txt** — a production crawler must respect `robots.txt` and `Crawl-delay`. Implementing it correctly (per-path rules, wildcards, delay handling) is nontrivial on its own.

**Rate limiting** — no deliberate delay between requests to the same host. The thread pool implicitly bounds concurrency, but a production crawler would add a configurable `Crawl-delay` per domain.

**Retry with backoff** — transient network errors (timeouts, 503s) are treated as permanent failures. A production crawler would retry with exponential backoff and a configurable retry limit.

**Content-Type filtering** — the crawler attempts to parse all responses as HTML regardless of content type. Checking `Content-Type` headers before parsing would skip PDFs, images, and other non-HTML responses.

**URL canonicalisation** — the current normalisation handles fragments and trailing slashes but not URL encoding differences (`/caf%C3%A9` vs `/café`) or query parameter ordering (`?a=1&b=2` vs `?b=2&a=1`). A more robust implementation would use a canonical form library.

**Streaming output** — results are printed as each BFS level completes rather than page-by-page. Switching to a continuous model would allow page-by-page output but complicates termination detection.
