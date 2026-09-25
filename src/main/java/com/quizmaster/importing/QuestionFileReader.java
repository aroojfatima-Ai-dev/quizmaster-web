package com.quizmaster.importing;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Turns an uploaded file into plain text.
 *
 * <table>
 *   <caption>Supported formats</caption>
 *   <tr><td>{@code .txt}</td><td>read by the JDK as UTF-8</td></tr>
 *   <tr><td>{@code .pdf}</td><td>read with Apache PDFBox</td></tr>
 *   <tr><td>{@code .docx}</td><td>read with Apache POI</td></tr>
 * </table>
 *
 * <p>Text extraction breaks long lines wherever the page ends; the parser joins
 * them back up, so nothing here tries to be clever about layout.
 */
public final class QuestionFileReader {

    private QuestionFileReader() {
    }

    /** Thrown when the file cannot be read at all - the message is shown to the teacher. */
    public static class UnreadableFileException extends RuntimeException {
        public UnreadableFileException(String message) {
            super(message);
        }

        public UnreadableFileException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * @param bytes    the uploaded file
     * @param fileName the original name, used to pick the format
     * @return the extracted text
     * @throws UnreadableFileException when the extension is unsupported or the file cannot be read
     */
    public static String read(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) {
            throw new UnreadableFileException("The file is empty.");
        }
        String extension = extensionOf(fileName);
        try {
            return switch (extension) {
                case "txt", "text", "csv" -> new String(bytes, StandardCharsets.UTF_8);
                case "pdf" -> readPdf(bytes);
                case "docx" -> readDocx(bytes);
                case "doc" -> throw new UnreadableFileException(
                        "Old Word .doc files are not supported - save the file as .docx or .txt and upload it again.");
                default -> throw new UnreadableFileException(
                        "Unsupported file type \"." + extension + "\". Please upload a PDF, Word (.docx) or text (.txt) file.");
            };
        } catch (IOException ex) {
            throw new UnreadableFileException("Could not read " + fileName + ": " + ex.getMessage(), ex);
        }
    }

    /** The human-readable format name shown on the preview screen. */
    public static String describeType(String fileName) {
        return switch (extensionOf(fileName)) {
            case "pdf" -> "PDF";
            case "docx" -> "Word document";
            case "doc" -> "Word document (legacy)";
            default -> "Text file";
        };
    }

    private static String readPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException ex) {
            throw new UnreadableFileException(
                    "The PDF could not be read (it may be password protected or an image-only scan).", ex);
        }
    }

    private static String readDocx(byte[] bytes) throws IOException {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append('\n');
            }
        } catch (IOException ex) {
            throw new UnreadableFileException("The Word document could not be read.", ex);
        }
        return text.toString();
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT).trim();
    }
}
