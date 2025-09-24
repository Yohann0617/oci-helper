package com.yohann.ocihelper.utils.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
@Slf4j
public class DuckDuckGoSearchService {

    private Mono<List<String>> searchWikipedia(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://en.wikipedia.org/api/rest_v1/page/summary/" + encodedQuery;
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Mozilla/5.0");
            int responseCode = con.getResponseCode();
            if (responseCode != 200) {
                return Mono.just(new ArrayList<>());
            }

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            return Mono.just(List.of(response.toString()));
        } catch (Exception e) {
            return Mono.just(new ArrayList<>());
        }
    }

    private Mono<List<String>> searchDuckDuckGo(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://api.duckduckgo.com/?q=" + encodedQuery + "&format=json&no_html=1";
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Mozilla/5.0");
            int responseCode = con.getResponseCode();
            if (responseCode != 200) {
                return Mono.just(new ArrayList<>());
            }

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            return Mono.just(List.of(response.toString()));
        } catch (Exception e) {
            return Mono.just(new ArrayList<>());
        }
    }

    private Mono<List<String>> searchHtmlDuckDuckGo(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://duckduckgo.com/html/?q=" + encodedQuery;

            // 建立连接
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36");
            con.setConnectTimeout(10000);
            con.setReadTimeout(10000);

            int responseCode = con.getResponseCode();
            if (responseCode != 200) {
                return Mono.just(new ArrayList<>());
            }

            // 读取 HTML
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            // 用正则解析 <a class="result__a">标题</a>
            List<String> results = new ArrayList<>();
            Pattern pattern = Pattern.compile("<a[^>]+class=\"result__a\"[^>]*>(.*?)</a>");
            Matcher matcher = pattern.matcher(response.toString());
            while (matcher.find() && results.size() < 5) {
                String text = matcher.group(1)
                        .replaceAll("<.*?>", "")      // 去掉 HTML 标签
                        .replaceAll("&amp;", "&")
                        .replaceAll("&quot;", "\"")
                        .replaceAll("&lt;", "<")
                        .replaceAll("&gt;", ">")
                        .trim();
                if (!text.isEmpty()) {
                    results.add(text);
                }
            }

            return Mono.just(results);

        } catch (Exception e) {
            e.printStackTrace();
            return Mono.just(new ArrayList<>());
        }
    }

    /**
     * 聚合搜索
     *
     * @param query 关键词
     * @return 搜索结果
     */
    public Mono<List<String>> search(String query) {
        // Wikipedia 搜索
        Mono<List<String>> wikiMono = searchWikipedia(query)
                .doOnNext(results -> log.info("关键词：[{}]，Wikipedia 搜索结果：{}", query.trim(), results));

        // DuckDuckGo JSON 搜索
        Mono<List<String>> duckDuckGoMono = searchDuckDuckGo(query)
                .doOnNext(results -> log.info("关键词：[{}]，DuckDuckGo 搜索结果：{}", query.trim(), results));

        // DuckDuckGo HTML 搜索
        Mono<List<String>> duckDuckGoHtmlMono = searchHtmlDuckDuckGo(query)
                .doOnNext(results -> log.info("关键词：[{}]，DuckDuckGo HTML 搜索结果：{}", query.trim(), results));

        // 并行执行三个搜索
        return Mono.zip(wikiMono, duckDuckGoMono, duckDuckGoHtmlMono)
                .map(tuple3 -> {
                    List<String> combined = new ArrayList<>();
                    combined.addAll(tuple3.getT1()); // Wikipedia
                    combined.addAll(tuple3.getT2()); // DuckDuckGo JSON
                    combined.addAll(tuple3.getT3()); // DuckDuckGo HTML
                    return combined;
                });
    }

    /**
     * 聚合搜索
     *
     * @param query 关键词
     * @return 搜索结果
     */
    public Mono<List<String>> searchWithHtml(String query) {
        Mono<List<String>> htmlMono = searchHtmlDuckDuckGo(query)
                .doOnNext(results -> log.info("关键词：[{}]，HTML 搜索结果：{}", query.trim(), results));

        // 执行搜索
        return Mono.just(Objects.requireNonNull(htmlMono.block(Duration.ofSeconds(60))));
    }
}