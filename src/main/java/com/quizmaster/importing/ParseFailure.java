package com.quizmaster.importing;

/**
 * A block in the uploaded file that could not be imported, with the line
 * number it came from and the reason, so the preview screen can list it
 * instead of silently dropping it.
 */
public record ParseFailure(int lineNumber, String reason, String preview) {

    public ParseFailure(int lineNumber, String reason, String preview) {
        this.lineNumber = lineNumber;
        this.reason = reason;
        this.preview = preview == null ? "" : preview.trim();
    }

    /** A short, single-line excerpt of the offending block. */
    public String getShortPreview() {
        if (preview.isEmpty()) {
            return "";
        }
        String single = preview.replaceAll("\\s+", " ");
        return single.length() > 110 ? single.substring(0, 110) + "…" : single;
    }

    public String getLineLabel() {
        return lineNumber > 0 ? "line " + lineNumber : "whole file";
    }
}
