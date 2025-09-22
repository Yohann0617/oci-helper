package com.yohann.ocihelper.utils.search;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.utils
 * @className: DuckDuckGoSearchService
 * @author: Yohann
 * @date: 2025/9/22 22:43
 */
@Service
public class DuckDuckGoSearchService {

    private final WebClient webClient = WebClient.builder().build();

    // 1. Wikipedia API
    public Mono<List<String>> searchWikipedia(String query) {
        try {
            String title = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://en.wikipedia.org/api/rest_v1/page/summary/" + title;

            return webClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .retrieve()
                    .bodyToMono(WikiResponse.class)
                    .map(resp -> {
                        List<String> results = new ArrayList<>();
                        if (resp.extract != null && !resp.extract.isEmpty()) {
                            results.add(resp.extract);
                        }
                        return results;
                    })
                    .onErrorReturn(new ArrayList<>()); // 错误返回空列表
        } catch (Exception e) {
            return Mono.just(new ArrayList<>());
        }
    }

    public static class WikiResponse {
        public String extract;
    }

    // 2. DuckDuckGo HTML 抓取（备用）
    public Mono<List<String>> searchDuckDuckGo(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("html.duckduckgo.com")
                            .path("/html/")
                            .queryParam("q", encodedQuery)
                            .build())
                    .header("User-Agent", "Mozilla/5.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(this::parseResults);
        } catch (Exception e) {
            return Mono.just(new ArrayList<>());
        }
    }

    private List<String> parseResults(String html) {
        List<String> results = new ArrayList<>();
        Pattern pattern = Pattern.compile("<a[^>]+class=\"result__a\"[^>]*>(.*?)</a>|<a[^>]+class=\"result__snippet\"[^>]*>(.*?)</a>");
        Matcher matcher = pattern.matcher(html);
        while (matcher.find() && results.size() < 5) {
            String text = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (text == null || text.isEmpty()) continue;

            text = text.replaceAll("<.*?>", "")
                    .replaceAll("&amp;", "&")
                    .replaceAll("&quot;", "\"")
                    .replaceAll("&lt;", "<")
                    .replaceAll("&gt;", ">")
                    .trim();

            if (text.toLowerCase().matches(".*(工具|online|yandex|htmlstrip).*")) continue;

            results.add(text);
        }
        return results;
    }

    // 3. 综合搜索：先 Wikipedia，再 DuckDuckGo
    public Mono<List<String>> search(String query) {
        return searchWikipedia(query)
                .flatMap(results -> {
                    if (!results.isEmpty()) {
                        return Mono.just(results);
                    } else {
                        return searchDuckDuckGo(query);
                    }
                });
    }
}