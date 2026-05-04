package org.example.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.example.exception.UnsupportedFileTypeException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

@Service
public class DocumentParserService {

    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/html",
            "application/rtf",
            "text/rtf",
            "application/vnd.oasis.opendocument.text"
    );

    private final Tika tika = new Tika();

    public record ParsedDocument(String text, String mimeType, String originalFilename) {
        public String titleFromFilename() {
            if (originalFilename == null || originalFilename.isBlank()) return "Untitled";
            int dot = originalFilename.lastIndexOf('.');
            String base = dot > 0 ? originalFilename.substring(0, dot) : originalFilename;
            return base.replace('_', ' ').replace('-', ' ').trim();
        }
    }

    public ParsedDocument parse(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        byte[] bytes = file.getBytes();
        String mimeType = tika.detect(bytes, file.getOriginalFilename());

        if (!SUPPORTED_MIME_TYPES.contains(mimeType)) {
            throw new UnsupportedFileTypeException(mimeType);
        }

        Metadata meta = new Metadata();
        meta.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());

        String text;
        try {
            text = tika.parseToString(new ByteArrayInputStream(bytes), meta);
        } catch (TikaException e) {
            throw new IllegalArgumentException("Failed to parse document content: " + e.getMessage(), e);
        }

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("No text content could be extracted from the file");
        }

        return new ParsedDocument(text.trim(), mimeType, file.getOriginalFilename());
    }
}
