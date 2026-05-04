package org.example.service;

import org.example.exception.UnsupportedFileTypeException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.*;

class DocumentParserServiceTest {

    private final DocumentParserService service = new DocumentParserService();

    @Test
    void parse_plainText_extractsContent() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "hello.txt", "text/plain", "Hello world document".getBytes());

        var result = service.parse(file);

        assertThat(result.text()).contains("Hello world document");
        assertThat(result.mimeType()).isEqualTo("text/plain");
        assertThat(result.originalFilename()).isEqualTo("hello.txt");
    }

    @Test
    void parse_htmlFile_stripsTagsAndExtractsText() throws Exception {
        String html = "<html><body><h1>Title</h1><p>Body text here</p></body></html>";
        MockMultipartFile file = new MockMultipartFile(
                "file", "page.html", "text/html", html.getBytes());

        var result = service.parse(file);

        assertThat(result.text()).contains("Title").contains("Body text here");
        assertThat(result.text()).doesNotContain("<h1>").doesNotContain("<p>");
    }

    @Test
    void parse_emptyFile_throwsIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.parse(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void parse_unsupportedType_throwsUnsupportedFileTypeException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "archive.zip", "application/zip",
                new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});

        assertThatThrownBy(() -> service.parse(file))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void titleFromFilename_stripsExtensionAndReplacesUnderscores() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "my_design_doc.txt", "text/plain", "some content here".getBytes());

        var result = service.parse(file);

        assertThat(result.titleFromFilename()).isEqualTo("my design doc");
    }

    @Test
    void titleFromFilename_replacesHyphens() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "api-reference-guide.txt", "text/plain", "content about api".getBytes());

        var result = service.parse(file);

        assertThat(result.titleFromFilename()).isEqualTo("api reference guide");
    }

    @Test
    void titleFromFilename_nullFilename_returnsUntitled() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "text/plain", "some content here".getBytes());

        var result = service.parse(file);

        assertThat(result.titleFromFilename()).isEqualTo("Untitled");
    }

    @Test
    void parse_rtfContent_extractsText() throws Exception {
        // Minimal valid RTF: \pard opens a paragraph, \par closes it
        byte[] rtf = "{\\rtf1\\ansi\\pard Hello RTF World\\par}".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.rtf", "application/rtf", rtf);

        var result = service.parse(file);

        assertThat(result.text()).contains("Hello RTF World");
    }
}
