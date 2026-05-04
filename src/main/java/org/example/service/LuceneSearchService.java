package org.example.service;

import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.example.model.Document;
import org.example.model.SearchResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LuceneSearchService {

    private final Map<String, TenantIndex> indexes = new ConcurrentHashMap<>();

    private static class TenantIndex {
        final Directory directory;
        final IndexWriter writer;
        volatile DirectoryReader reader;

        TenantIndex() throws IOException {
            directory = new ByteBuffersDirectory();
            writer = new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()));
            writer.commit();
            reader = DirectoryReader.open(writer);
        }

        synchronized void refreshReader() throws IOException {
            DirectoryReader updated = DirectoryReader.openIfChanged(reader, writer);
            if (updated != null) {
                reader.close();
                reader = updated;
            }
        }
    }

    private TenantIndex getOrCreate(String tenantId) {
        return indexes.computeIfAbsent(tenantId, k -> {
            try {
                return new TenantIndex();
            } catch (IOException e) {
                throw new RuntimeException("Failed to create index for tenant: " + tenantId, e);
            }
        });
    }

    public void indexDocument(Document doc) throws IOException {
        TenantIndex idx = getOrCreate(doc.getTenantId());
        synchronized (idx) {
            idx.writer.deleteDocuments(new Term("id", doc.getId()));
            idx.writer.addDocument(toLuceneDoc(doc));
            idx.writer.commit();
            idx.refreshReader();
        }
    }

    public void deleteDocument(String tenantId, String documentId) throws IOException {
        TenantIndex idx = getOrCreate(tenantId);
        synchronized (idx) {
            idx.writer.deleteDocuments(new Term("id", documentId));
            idx.writer.commit();
            idx.refreshReader();
        }
    }

    public SearchQueryResult search(String tenantId, String queryStr, int page, int pageSize) throws IOException, ParseException {
        TenantIndex idx = getOrCreate(tenantId);
        IndexSearcher searcher = new IndexSearcher(idx.reader);

        MultiFieldQueryParser parser = new MultiFieldQueryParser(
                new String[]{"title", "content"},
                new StandardAnalyzer()
        );
        Query query = parser.parse(queryStr);

        int numHits = Math.max((page + 1) * pageSize, 1);
        TopDocs topDocs = searcher.search(query, numHits);
        long total = topDocs.totalHits.value;

        List<SearchResult.Hit> hits = new ArrayList<>();
        int start = page * pageSize;
        int end = (int) Math.min(start + pageSize, topDocs.scoreDocs.length);

        for (int i = start; i < end; i++) {
            ScoreDoc scoreDoc = topDocs.scoreDocs[i];
            org.apache.lucene.document.Document luceneDoc = searcher.storedFields().document(scoreDoc.doc);
            hits.add(SearchResult.Hit.builder()
                    .id(luceneDoc.get("id"))
                    .title(luceneDoc.get("title"))
                    .contentSnippet(snippet(luceneDoc.get("content"), 200))
                    .score(scoreDoc.score)
                    .createdAt(luceneDoc.get("createdAt"))
                    .build());
        }

        return new SearchQueryResult(hits, total);
    }

    public boolean documentExists(String tenantId, String documentId) throws IOException {
        TenantIndex idx = getOrCreate(tenantId);
        IndexSearcher searcher = new IndexSearcher(idx.reader);
        TopDocs docs = searcher.search(new TermQuery(new Term("id", documentId)), 1);
        return docs.totalHits.value > 0;
    }

    public long getDocumentCount(String tenantId) {
        TenantIndex idx = indexes.get(tenantId);
        return idx != null ? idx.reader.numDocs() : 0;
    }

    public long getTotalIndexedDocuments() {
        return indexes.values().stream().mapToLong(i -> i.reader.numDocs()).sum();
    }

    public int getIndexedTenantCount() {
        return indexes.size();
    }

    @PreDestroy
    public void shutdown() {
        indexes.values().forEach(idx -> {
            try {
                idx.reader.close();
                idx.writer.close();
            } catch (IOException ignored) {}
        });
    }

    private org.apache.lucene.document.Document toLuceneDoc(Document doc) {
        org.apache.lucene.document.Document ld = new org.apache.lucene.document.Document();
        ld.add(new StringField("id", doc.getId(), Field.Store.YES));
        ld.add(new StringField("tenantId", doc.getTenantId(), Field.Store.YES));
        ld.add(new TextField("title", doc.getTitle(), Field.Store.YES));
        ld.add(new TextField("content", doc.getContent(), Field.Store.YES));
        ld.add(new StoredField("createdAt", doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : ""));
        return ld;
    }

    private String snippet(String content, int maxLen) {
        if (content == null || content.isEmpty()) return "";
        return content.length() <= maxLen ? content : content.substring(0, maxLen) + "...";
    }

    public record SearchQueryResult(List<SearchResult.Hit> hits, long total) {}
}
