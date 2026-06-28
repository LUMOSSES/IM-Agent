package com.aiagent.chatagent.controller;

import com.aiagent.chatagent.model.dto.KnDocInfo;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
public class KnowledgeController {

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    @Resource
    private EmbeddingStoreIngestor embeddingStoreIngestor;

    @Value("${rag.docs-path}")
    private String docsPath;

    @GetMapping("/knowledge/documents")
    public List<KnDocInfo> listDocuments() {
        List<KnDocInfo> docs = new ArrayList<>();
        Path dir = Paths.get(docsPath);
        if (!Files.isDirectory(dir)) return docs;

        try (var stream = Files.newDirectoryStream(dir, "*.md")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                long size = Files.size(file);
                long lastModified = Files.getLastModifiedTime(file).toMillis();
                int qaCount = countQuestions(file);
                docs.add(new KnDocInfo(name, size, lastModified, qaCount));
            }
        } catch (IOException e) {
            log.error("扫描文档目录失败", e);
        }
        docs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return docs;
    }

    @GetMapping("/knowledge/document")
    public Map<String, Object> getDocument(@RequestParam("name") String name) {
        Path file = resolveSafe(name);
        if (file == null || !Files.isRegularFile(file)) {
            return Map.of("error", "文档不存在");
        }
        try {
            String content = Files.readString(file);
            return Map.of("name", name, "content", content);
        } catch (IOException e) {
            log.error("读取文档失败: {}", name, e);
            return Map.of("error", "读取失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/knowledge/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadDocument(@RequestParam("file") MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.endsWith(".md")) {
            return Map.of("error", "仅支持 .md 文件");
        }
        String safeName = Paths.get(originalName).getFileName().toString();

        try {
            Path dir = Paths.get(docsPath);
            Files.createDirectories(dir);

            Path target = dir.resolve(safeName);
            // Read content as UTF-8 string for ingestion
            String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);

            // Check if file exists and decide whether to append or overwrite
            boolean existed = Files.exists(target);

            // Write file (overwrite or create)
            Files.writeString(target, content);

            // Ingest: wrap entire file content as one Document, split by paragraph
            // If replacing, first remove old embeddings
            if (existed) {
                try {
                    Filter f = MetadataFilterBuilder.metadataKey("file_name").isEqualTo(safeName);
                    embeddingStore.removeAll(f);
                } catch (Exception e) {
                    log.warn("清除旧向量失败: {}", e.getMessage());
                }
            }

            Metadata metadata = Metadata.from("file_name", safeName);
            Document doc = Document.from(content, metadata);
            embeddingStoreIngestor.ingest(doc);

            log.info("文档上传成功: {} ({} bytes)", safeName, content.length());
            return Map.of("success", true, "name", safeName, "existed", existed);
        } catch (IOException e) {
            log.error("上传文档失败: {}", safeName, e);
            return Map.of("error", "上传失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/knowledge/document")
    public Map<String, Object> deleteDocument(@RequestParam("name") String name) {
        Path file = resolveSafe(name);
        if (file == null || !Files.isRegularFile(file)) {
            return Map.of("error", "文档不存在");
        }

        try {
            // 1. Move to trash
            Path trashDir = Paths.get(docsPath, "trash");
            Files.createDirectories(trashDir);
            Path trashTarget = trashDir.resolve(name);
            Files.move(file, trashTarget, StandardCopyOption.REPLACE_EXISTING);

            // 2. Remove embeddings
            Filter f = MetadataFilterBuilder.metadataKey("file_name").isEqualTo(name);
            embeddingStore.removeAll(f);

            log.info("文档已删除: {} → trash/", name);
            return Map.of("success", true, "name", name);
        } catch (IOException e) {
            log.error("删除文档失败: {}", name, e);
            return Map.of("error", "删除失败: " + e.getMessage());
        }
    }

    private Path resolveSafe(String name) {
        if (name == null || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return null;
        }
        Path file = Paths.get(docsPath, name);
        if (!file.normalize().startsWith(Paths.get(docsPath).normalize())) {
            return null;
        }
        return file;
    }

    private int countQuestions(Path file) {
        try {
            return (int) Files.readAllLines(file).stream()
                    .filter(line -> line.trim().startsWith("### Q：") || line.trim().startsWith("### Q:"))
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }
}
