package org.example.exception;

public class UnsupportedFileTypeException extends RuntimeException {
    public UnsupportedFileTypeException(String mimeType) {
        super("Unsupported file type: " + mimeType + ". Supported types: PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, TXT, HTML, RTF, ODT");
    }
}
