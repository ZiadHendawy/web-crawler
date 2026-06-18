package com.webcrawler;

import java.util.List;

public record PageResult(String url, List<String> links) {}
