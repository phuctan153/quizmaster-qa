package com.quizmaster.exams;

import com.quizmaster.questions.Question;
import com.quizmaster.questions.Difficulty;
import com.quizmaster.questions.QuestionRepository;
import com.quizmaster.shared.ApiException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamService {
    private final ExamRepository exams;
    private final QuestionRepository questions;
    private final Clock clock;

    public ExamService(ExamRepository exams, QuestionRepository questions, Clock clock) {
        this.exams = exams;
        this.questions = questions;
        this.clock = clock;
    }

    @Transactional
    public Exam create(UUID ownerId, ExamRequests.Input request) {
        validate(request);
        ownedQuestions(ownerId, request.questionIds());
        var exam = new Exam(UUID.randomUUID(), UUID.randomUUID(), 1, ownerId, request.title(), ExamState.DRAFT,
                request.durationSeconds().intValueExact(), request.attemptLimit().intValueExact(), request.passingThreshold(), request.shuffle(), 0,
                request.questionIds(), List.of());
        exams.insertDraft(exam, clock.instant());
        return read(exam.id(), ownerId);
    }

    @Transactional(readOnly = true)
    public ExamPage list(UUID ownerId, int page, int size) {
        validatePage(page, size);
        return new ExamPage(exams.list(ownerId, page, size), page, size, exams.count(ownerId));
    }

    @Transactional(readOnly = true)
    public Exam read(UUID id, UUID ownerId) {
        return exams.byId(id, ownerId).orElseThrow(ExamService::notFound);
    }

    @Transactional
    public Exam update(UUID id, UUID ownerId, ExamRequests.Update request) {
        validate(request.input());
        var draft = requireDraft(id, ownerId, request.revision());
        var selected = resolveQuestions(ownerId, request.questionIds(), draft.questions());
        if (!exams.updateDraft(id, ownerId, request, selected, clock.instant())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVISION_CONFLICT", "Exam revision is stale");
        }
        return read(id, ownerId);
    }

    @Transactional
    public Exam publish(UUID id, UUID ownerId, ExamRequests.Revision request) {
        var draft = requireDraft(id, ownerId, request.revision());
        if (draft.questionIds().isEmpty()) throw invalid("A published exam must contain at least one question");
        var selected = resolveQuestions(ownerId, draft.questionIds(), draft.questions());
        if (!exams.publish(id, ownerId, request.revision(), selected, clock.instant())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVISION_CONFLICT", "Exam revision is stale");
        }
        return read(id, ownerId);
    }

    @Transactional
    public Exam createSuccessor(UUID sourceId, UUID ownerId) {
        var source = exams.lockById(sourceId, ownerId).orElseThrow(ExamService::notFound);
        if (source.state() == ExamState.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "A successor requires a published or closed source");
        }
        if (source.questions().isEmpty()) throw invalid("A source exam must contain a published snapshot");
        var successor = new Exam(UUID.randomUUID(), source.familyId(), exams.nextVersionNumber(source.familyId()), ownerId,
                source.title(), ExamState.DRAFT, source.durationSeconds(), source.attemptLimit(), source.passingThreshold(),
                source.shuffle(), 0, source.questionIds(), source.questions());
        exams.insertSuccessor(successor, clock.instant());
        return read(successor.id(), ownerId);
    }

    @Transactional
    public Exam close(UUID id, UUID ownerId) {
        var exam = exams.byId(id, ownerId).orElseThrow(ExamService::notFound);
        if (exam.state() == ExamState.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "A draft exam cannot be closed");
        }
        if (exam.state() == ExamState.PUBLISHED) exams.close(id, ownerId, clock.instant());
        return read(id, ownerId);
    }

    private Exam requireDraft(UUID id, UUID ownerId, int revision) {
        var exam = exams.byId(id, ownerId).orElseThrow(ExamService::notFound);
        if (exam.state() != ExamState.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "Only a draft exam can be changed");
        }
        if (exam.revision() != revision) {
            throw new ApiException(HttpStatus.CONFLICT, "REVISION_CONFLICT", "Exam revision is stale");
        }
        return exam;
    }

    private List<Question> ownedQuestions(UUID ownerId, List<UUID> ids) {
        var result = new ArrayList<Question>();
        for (UUID id : ids) result.add(questions.byId(id, ownerId).orElseThrow(ExamService::notFound));
        return List.copyOf(result);
    }

    private List<Question> resolveQuestions(UUID ownerId, List<UUID> ids, List<Exam.QuestionSnapshot> pinnedSnapshots) {
        var pinned = pinnedSnapshots.stream().collect(java.util.stream.Collectors.toMap(
                Exam.QuestionSnapshot::sourceQuestionId, snapshot -> snapshot));
        var selected = new ArrayList<Question>();
        for (UUID id : ids) {
            var question = questions.byId(id, ownerId).orElseThrow(ExamService::notFound);
            var snapshot = pinned.get(id);
            if (snapshot == null) {
                selected.add(question);
            } else {
                var options = snapshot.options().stream().map(option -> new Question.Option(
                        option.id(), option.label(), option.text(), option.correct())).toList();
                selected.add(new Question(question.id(), ownerId, question.subjectId(), snapshot.prompt(),
                        snapshot.difficulty() == null ? null : Difficulty.valueOf(snapshot.difficulty()), 0, options));
            }
        }
        return List.copyOf(selected);
    }

    private static void validate(ExamRequests.Input request) {
        if (request.questionIds() == null || request.questionIds().stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("Question IDs are required");
        }
        if (new HashSet<>(request.questionIds()).size() != request.questionIds().size()) {
            throw invalid("Question IDs must be unique");
        }
        if (request.shuffle() == null) throw invalid("Shuffle must be specified");
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw invalid("Invalid pagination");
    }
    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"); }
}
