package com.webcrawler;

import java.io.IOException;

public interface Fetcher {
    String fetch(String url) throws IOException;
}
