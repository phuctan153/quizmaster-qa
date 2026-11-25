package com.quizmaster.assignments;

import com.quizmaster.shared.ApiException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentService {
    public record AssignmentResult(Assignment assignment, boolean created) {}
    private final AssignmentRepository assignments;
    private final Clock clock;

    public AssignmentService(AssignmentRepository assignments, Clock clock) {
        this.assignments = assignments;
        this.clock = clock;
    }

    @Transactional
    public AssignmentResult assign(UUID examId, UUID instructorId, UUID studentId) {
        if (!assignments.lockPublishedExamExists(examId, instructorId)) {
            if (!assignments.ownedExamExists(examId, instructorId)) throw notFound();
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "Only a published exam can be assigned");
        }
        if (assignments.assignmentExists(examId, studentId)) {
            return new AssignmentResult(assignments.assign(examId, studentId, clock.instant()), false);
        }
        if (assignments.student(studentId).isEmpty()) {
            if (assignments.accountExists(studentId)) {
                throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "Recipient must be an active student");
            }
            throw notFound();
        }
        return new AssignmentResult(assignments.assign(examId, studentId, clock.instant()), true);
    }

    @Transactional(readOnly = true)
    public AssignmentPage<Assignment> roster(UUID examId, UUID instructorId, int page, int size) {
        validatePage(page, size);
        if (!assignments.ownedExamExists(examId, instructorId)) throw notFound();
        return new AssignmentPage<>(assignments.roster(examId, page, size), page, size, assignments.rosterCount(examId));
    }

    @Transactional(readOnly = true)
    public AssignmentPage<AssignedExam> assigned(UUID studentId, int page, int size) {
        validatePage(page, size);
        return new AssignmentPage<>(assignments.forStudent(studentId, page, size), page, size, assignments.studentCount(studentId));
    }

    @Transactional(readOnly = true)
    public AssignmentPage<StudentSummary> students(int page, int size) {
        validatePage(page, size);
        return new AssignmentPage<>(assignments.activeStudents(page, size), page, size, assignments.activeStudentCount());
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid pagination");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }
}
