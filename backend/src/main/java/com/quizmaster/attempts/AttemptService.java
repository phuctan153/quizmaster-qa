package com.quizmaster.attempts;

import com.quizmaster.shared.ApiException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttemptService {
    public record StartResult(AttemptView attempt, boolean created, String conflictCode) {}
    private final AttemptRepository attempts;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AttemptService(AttemptRepository attempts, Clock clock) {
        this.attempts = attempts;
        this.clock = clock;
    }

    @Transactional
    public StartResult start(UUID examId, UUID studentId) {
        var version = attempts.lockVersion(examId).orElseThrow(AttemptService::notFound);
        var assignment = attempts.lockAssignment(examId, studentId).orElseThrow(AttemptService::notFound);
        var active = attempts.lockActiveAttempt(assignment.id());
        Instant now = clock.instant();
        boolean expiredPrevious = false;
        if (active.isPresent()) {
            var current = active.get();
            if (now.isBefore(current.deadline())) return new StartResult(attempts.view(current), false, null);
            attempts.expire(current, version.passingThreshold(), now);
            expiredPrevious = true;
        }
        if (version.state() != com.quizmaster.exams.ExamState.PUBLISHED) {
            if (expiredPrevious) return new StartResult(null, false, "STATE_CONFLICT");
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "Only a published exam can be started");
        }
        if (assignment.allocatedCount() >= version.attemptLimit()) {
            if (expiredPrevious) return new StartResult(null, false, "ATTEMPT_LIMIT");
            throw new ApiException(HttpStatus.CONFLICT, "ATTEMPT_LIMIT", "Attempt limit has been reached");
        }
        var questionIds = new ArrayList<>(attempts.snapshotQuestionIds(examId));
        if (questionIds.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "Published exam has no question snapshot");
        if (version.shuffle()) Collections.shuffle(questionIds, random);
        attempts.allocate(assignment.id(), assignment.allocatedCount());
        var attempt = attempts.insert(UUID.randomUUID(), assignment.id(), examId, studentId,
                assignment.allocatedCount() + 1, now, now.plusSeconds(version.durationSeconds()), questionIds);
        return new StartResult(attempts.view(attempt), true, null);
    }

    @Transactional
    public AttemptView read(UUID attemptId, UUID studentId) {
        var attempt = attempts.lockStudentAttempt(attemptId, studentId).orElseThrow(AttemptService::notFound);
        Instant now = clock.instant();
        if (attempt.state() == AttemptState.IN_PROGRESS && !now.isBefore(attempt.deadline())) {
            var threshold = attempts.passingThreshold(attempt.examId());
            attempts.expire(attempt, threshold, now);
            attempt = new AttemptRepository.Header(attempt.id(), attempt.assignmentId(), attempt.examId(), attempt.studentId(),
                    attempt.attemptNumber(), AttemptState.EXPIRED, attempt.startedAt(), attempt.deadline(), now);
        }
        return attempts.view(attempt);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }
}
