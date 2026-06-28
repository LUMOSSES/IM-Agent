package com.aiagent.chatagent.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SourceTrackingContentRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(SourceTrackingContentRetriever.class);

    private final ContentRetriever delegate;
    private static final ThreadLocal<List<SourceInfo>> sourcesHolder = new ThreadLocal<>();

    public SourceTrackingContentRetriever(ContentRetriever delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Content> retrieve(Query query) {
        List<Content> contents = delegate.retrieve(query);
        List<SourceInfo> sources = new ArrayList<>();
        for (Content content : contents) {
            TextSegment segment = content.textSegment();
            String fileName = segment.metadata().getString("file_name");
            if (fileName == null || fileName.isEmpty()) {
                fileName = "unknown";
            }
            String text = segment.text();
            String snippet = text != null && text.length() > 200
                    ? text.substring(0, 200) + "..."
                    : text;
            double score = extractScore(segment);
            sources.add(new SourceInfo(fileName, snippet, score));
        }
        sourcesHolder.set(sources);
        log.debug("RAG 检索到 {} 个来源", sources.size());
        return contents;
    }

    private double extractScore(TextSegment segment) {
        try {
            Object scoreObj = segment.metadata().toMap().get("score");
            if (scoreObj instanceof Number num) return num.doubleValue();
        } catch (Exception ignored) {
        }
        return 0.0;
    }

    public static List<SourceInfo> getAndClearSources() {
        List<SourceInfo> sources = sourcesHolder.get();
        sourcesHolder.remove();
        return sources;
    }
}
