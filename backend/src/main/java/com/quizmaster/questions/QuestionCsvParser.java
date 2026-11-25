package com.quizmaster.questions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class QuestionCsvParser {
    static final int MAX_BYTES = 1024 * 1024;
    static final int MAX_ROWS = 500;
    static final List<String> HEADER = List.of("subjectId", "prompt", "optionA", "optionB", "optionC", "optionD", "correctLabel");

    record Row(int rowNumber, UUID subjectId, String prompt, List<String> options, String correctLabel) {}
    record ParseResult(List<Row> rows, List<ImportError> errors) {}
    record ImportError(String field, String message) {}

    ParseResult parse(byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw new FileTooLargeException();
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            return new ParseResult(List.of(), List.of(new ImportError("file", "File must use valid UTF-8")));
        }
        if (text.startsWith("\uFEFF")) text = text.substring(1);

        List<List<String>> records;
        try {
            records = records(text);
        } catch (IllegalArgumentException ex) {
            return new ParseResult(List.of(), List.of(new ImportError("file", "CSV quoting is invalid")));
        }
        if (records.isEmpty() || !records.getFirst().equals(HEADER)) {
            return new ParseResult(List.of(), List.of(new ImportError("header", "CSV header does not match the required columns")));
        }
        var errors = new ArrayList<ImportError>();
        var rows = new ArrayList<Row>();
        int dataRows = 0;
        for (int index = 1; index < records.size(); index++) {
            var fields = records.get(index);
            if (fields.stream().allMatch(String::isBlank)) continue;
            dataRows++;
            int rowNumber = index + 1;
            if (dataRows > MAX_ROWS) {
                errors.add(new ImportError("rows[" + rowNumber + "]", "Import cannot exceed 500 data rows"));
                break;
            }
            int startErrors = errors.size();
            if (fields.size() != HEADER.size()) {
                errors.add(new ImportError("rows[" + rowNumber + "]", "Expected exactly 7 columns"));
                continue;
            }
            UUID subjectId = null;
            try { subjectId = UUID.fromString(fields.get(0).trim()); }
            catch (IllegalArgumentException ex) { errors.add(new ImportError(path(rowNumber, "subjectId"), "Must be a UUID")); }

            String prompt = fields.get(1).trim();
            if (prompt.isEmpty() || prompt.length() > 2000) errors.add(new ImportError(path(rowNumber, "prompt"), "Must contain 1 to 2000 characters"));
            var options = fields.subList(2, 6).stream().map(String::trim).toList();
            for (int option = 0; option < options.size(); option++) {
                String value = options.get(option);
                if (value.isEmpty() || value.length() > 500) {
                    errors.add(new ImportError(path(rowNumber, "option" + (char) ('A' + option)), "Must contain 1 to 500 characters"));
                }
            }
            if (options.stream().map(value -> value.toLowerCase(java.util.Locale.ROOT)).distinct().count() != options.size()) {
                errors.add(new ImportError(path(rowNumber, "options"), "Option texts must be unique"));
            }
            String correctLabel = fields.get(6).trim().toUpperCase(java.util.Locale.ROOT);
            if (!List.of("A", "B", "C", "D").contains(correctLabel)) {
                errors.add(new ImportError(path(rowNumber, "correctLabel"), "Must be A, B, C or D"));
            }
            if (errors.size() == startErrors) rows.add(new Row(rowNumber, subjectId, prompt, options, correctLabel));
        }
        if (dataRows == 0) errors.add(new ImportError("rows", "CSV must include at least one data row"));
        return new ParseResult(List.copyOf(rows), List.copyOf(errors));
    }

    private static String path(int row, String field) { return "rows[" + row + "]." + field; }

    private static List<List<String>> records(String text) {
        var rows = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        closedQuote = true;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == '"') {
                if (field.length() != 0 || closedQuote) throw new IllegalArgumentException("Unexpected quote");
                quoted = true;
            } else if (character == ',') {
                row.add(field.toString());
                field.setLength(0);
                closedQuote = false;
            } else if (character == '\r' || character == '\n') {
                if (closedQuote || field.length() > 0 || !row.isEmpty()) {
                    row.add(field.toString());
                    rows.add(List.copyOf(row));
                }
                row.clear();
                field.setLength(0);
                closedQuote = false;
                if (character == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
            } else {
                if (closedQuote) throw new IllegalArgumentException("Unexpected data after closing quote");
                field.append(character);
            }
        }
        if (quoted) throw new IllegalArgumentException("Unclosed quote");
        if (closedQuote || field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(List.copyOf(row));
        }
        return rows;
    }

    static final class FileTooLargeException extends RuntimeException {}
}
