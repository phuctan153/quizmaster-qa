package com.quizmaster.questions;

import com.quizmaster.shared.ApiException;
import com.quizmaster.shared.ApiError;
import java.time.Clock;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {
    private final QuestionRepository repository;
    private final Clock clock;
    private final QuestionCsvParser csvParser = new QuestionCsvParser();

    public QuestionService(QuestionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Question create(UUID ownerId, QuestionRequests.Input request) {
        validate(request);
        if (!repository.subjectExists(request.subjectId())) throw notFound();
        var question = new Question(UUID.randomUUID(), ownerId, request.subjectId(), request.prompt(),
                request.difficulty(), 0, options(request));
        repository.insert(question, clock.instant());
        return question;
    }

    @Transactional(readOnly = true)
    public QuestionPage list(UUID ownerId, UUID subjectId, int page, int size) {
        validatePage(page, size);
        if (subjectId != null && !repository.subjectExists(subjectId)) throw notFound();
        return new QuestionPage(repository.list(ownerId, subjectId, page, size), page, size, repository.count(ownerId, subjectId));
    }

    @Transactional(readOnly = true)
    public Question get(UUID id, UUID ownerId) {
        return repository.byId(id, ownerId).orElseThrow(QuestionService::notFound);
    }

    @Transactional
    public Question update(UUID id, UUID ownerId, QuestionRequests.Update request) {
        validate(request.input());
        if (!repository.owned(id, ownerId)) throw notFound();
        if (!repository.subjectExists(request.subjectId())) throw notFound();
        if (!repository.update(id, ownerId, request, clock.instant())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVISION_CONFLICT", "Question revision is stale");
        }
        return get(id, ownerId);
    }

    @Transactional
    public QuestionImportResult importCsv(UUID ownerId, byte[] content) {
        QuestionCsvParser.ParseResult parsed;
        try {
            parsed = csvParser.parse(content);
        } catch (QuestionCsvParser.FileTooLargeException ex) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "CSV file exceeds 1 MiB");
        }
        var errors = new ArrayList<ApiError.FieldError>();
        parsed.errors().forEach(error -> errors.add(new ApiError.FieldError(error.field(), error.message())));
        for (var row : parsed.rows()) {
            if (!repository.subjectExists(row.subjectId())) {
                errors.add(new ApiError.FieldError("rows[" + row.rowNumber() + "].subjectId", "Subject does not exist"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "CSV import validation failed", errors);
        }
        var ids = new ArrayList<UUID>();
        for (var row : parsed.rows()) {
            var options = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(index -> new Question.Option(UUID.randomUUID(), String.valueOf((char) ('A' + index)),
                            row.options().get(index), String.valueOf((char) ('A' + index)).equals(row.correctLabel())))
                    .toList();
            var question = new Question(UUID.randomUUID(), ownerId, row.subjectId(), row.prompt(), null, 0, options);
            repository.insert(question, clock.instant());
            ids.add(question.id());
        }
        return new QuestionImportResult(ids.size(), ids);
    }

    private static void validate(QuestionRequests.Input request) {
        if (request.options() == null || request.options().stream().anyMatch(option -> option == null || option.correct() == null)) {
            throw invalid("Options are required");
        }
        long correct = request.options().stream().filter(option -> Boolean.TRUE.equals(option.correct())).count();
        if (correct != 1) throw invalid("Exactly one option must be correct");
        var labels = new HashSet<String>();
        var texts = new HashSet<String>();
        for (var option : request.options()) {
            if (!labels.add(normalize(option.label())) || !texts.add(normalize(option.text()))) {
                throw invalid("Option labels and texts must be unique");
            }
        }
    }

    private static java.util.List<Question.Option> options(QuestionRequests.Input request) {
        return request.options().stream().map(option -> new Question.Option(UUID.randomUUID(), option.label(),
                option.text(), option.correct())).toList();
    }

    private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw invalid("Invalid pagination");
    }
    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"); }
}
