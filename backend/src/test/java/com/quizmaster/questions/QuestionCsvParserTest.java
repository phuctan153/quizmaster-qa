package com.quizmaster.questions;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionCsvParserTest {
    private final QuestionCsvParser parser = new QuestionCsvParser();

    @Test
    void parsesQuotedCommasEscapedQuotesAndMultilineFields() {
        var subject = UUID.randomUUID();
        var csv = String.join(",", QuestionCsvParser.HEADER) + "\r\n" + subject
                + ",\"Which, answer?\nChoose one\",\"First, \"\"quoted\"\"\",Second,Third,Fourth,C\r\n";
        var result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().prompt()).isEqualTo("Which, answer?\nChoose one");
        assertThat(result.rows().getFirst().options().getFirst()).isEqualTo("First, \"quoted\"");
        assertThat(result.rows().getFirst().correctLabel()).isEqualTo("C");
    }

    @Test
    void rejectsMalformedQuotesAndInvalidUtf8() {
        var malformed = (String.join(",", QuestionCsvParser.HEADER) + "\n" + UUID.randomUUID()
                + ",\"unclosed,one,two,three,four,A").getBytes(StandardCharsets.UTF_8);
        assertThat(parser.parse(malformed).errors()).extracting(QuestionCsvParser.ImportError::field).containsExactly("file");
        assertThat(parser.parse(new byte[] {(byte) 0xc3, (byte) 0x28}).errors())
                .extracting(QuestionCsvParser.ImportError::field).containsExactly("file");
    }

    @Test
    void capsDataRowsAndFileBytes() {
        var csv = new StringBuilder(String.join(",", QuestionCsvParser.HEADER)).append('\n');
        for (int row = 0; row < QuestionCsvParser.MAX_ROWS + 1; row++) {
            csv.append(UUID.randomUUID()).append(",prompt,one,two,three,four,A\n");
        }
        assertThat(parser.parse(csv.toString().getBytes(StandardCharsets.UTF_8)).errors())
                .extracting(QuestionCsvParser.ImportError::field).contains("rows[502]");
        assertThatThrownBy(() -> parser.parse(new byte[QuestionCsvParser.MAX_BYTES + 1]))
                .isInstanceOf(QuestionCsvParser.FileTooLargeException.class);
    }
}
