package com.quizmaster.attempts;

import com.quizmaster.exams.ExamState;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AttemptRepository {
    record Version(UUID id, ExamState state, int durationSeconds, int attemptLimit, BigDecimal passingThreshold,
                   boolean shuffle) {}
    record AssignmentLock(UUID id, int allocatedCount) {}
    record Header(UUID id, UUID assignmentId, UUID examId, UUID studentId, int attemptNumber,
                  AttemptState state, Instant startedAt, Instant deadline, Instant finalizedAt) {}
    private static final class QuestionBuilder {
        final UUID id;
        final String prompt;
        final List<AttemptView.Option> options = new ArrayList<>();
        UUID selectedOptionId;
        int revision;
        QuestionBuilder(UUID id, String prompt) { this.id = id; this.prompt = prompt; }
        AttemptView.Question build() { return new AttemptView.Question(id, prompt, options, selectedOptionId, revision); }
    }

    private final JdbcClient jdbc;
    public AttemptRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    Optional<Version> lockVersion(UUID examId) {
        return jdbc.sql("SELECT id,state,duration_seconds,attempt_limit,passing_threshold,shuffle FROM exam_version WHERE id=:id FOR UPDATE")
                .param("id", examId).query((rs, row) -> new Version(rs.getObject("id", UUID.class),
                        ExamState.valueOf(rs.getString("state")), rs.getInt("duration_seconds"),
                        rs.getInt("attempt_limit"), rs.getBigDecimal("passing_threshold"), rs.getBoolean("shuffle"))).optional();
    }

    Optional<AssignmentLock> lockAssignment(UUID examId, UUID studentId) {
        return jdbc.sql("SELECT id,allocated_count FROM exam_assignment WHERE exam_version_id=:exam AND student_id=:student FOR UPDATE")
                .param("exam", examId).param("student", studentId)
                .query((rs, row) -> new AssignmentLock(rs.getObject("id", UUID.class), rs.getInt("allocated_count"))).optional();
    }

    Optional<Header> lockActiveAttempt(UUID assignmentId) {
        return jdbc.sql("SELECT * FROM attempt WHERE assignment_id=:assignment AND state='IN_PROGRESS' FOR UPDATE")
                .param("assignment", assignmentId).query(AttemptRepository::header).optional();
    }

    Optional<Header> lockStudentAttempt(UUID attemptId, UUID studentId) {
        return jdbc.sql("SELECT * FROM attempt WHERE id=:id AND student_id=:student FOR UPDATE")
                .param("id", attemptId).param("student", studentId).query(AttemptRepository::header).optional();
    }

    BigDecimal passingThreshold(UUID examId) {
        return jdbc.sql("SELECT passing_threshold FROM exam_version WHERE id=:exam")
                .param("exam", examId).query(BigDecimal.class).single();
    }

    void expire(Header attempt, BigDecimal passingThreshold, Instant now) {
        int total = jdbc.sql("SELECT count(*) FROM attempt_question WHERE attempt_id=:id")
                .param("id", attempt.id()).query(Integer.class).single();
        int correct = jdbc.sql("SELECT count(*) FROM attempt_question q JOIN answer a ON a.attempt_id=q.attempt_id AND a.exam_question_id=q.exam_question_id "
                        + "JOIN exam_option_snapshot o ON o.exam_question_id=q.exam_question_id AND o.id=a.selected_option_id AND o.correct=true "
                        + "WHERE q.attempt_id=:id")
                .param("id", attempt.id()).query(Integer.class).single();
        var score = BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        jdbc.sql("UPDATE attempt SET state='EXPIRED',finalized_at=:now WHERE id=:id AND state='IN_PROGRESS'")
                .param("now", Timestamp.from(now)).param("id", attempt.id()).update();
        jdbc.sql("INSERT INTO exam_result(attempt_id,correct_count,total_count,score,passed,finalized_at,reason) "
                        + "VALUES (:attempt,:correct,:total,:score,:passed,:now,'EXPIRED')")
                .param("attempt", attempt.id()).param("correct", correct).param("total", total)
                .param("score", score).param("passed", score.compareTo(passingThreshold) >= 0)
                .param("now", Timestamp.from(now)).update();
    }

    void allocate(UUID assignmentId, int currentCount) {
        int updated = jdbc.sql("UPDATE exam_assignment SET allocated_count=allocated_count+1 WHERE id=:id AND allocated_count=:count")
                .param("id", assignmentId).param("count", currentCount).update();
        if (updated != 1) throw new IllegalStateException("Locked assignment counter changed unexpectedly");
    }

    List<UUID> snapshotQuestionIds(UUID examId) {
        return jdbc.sql("SELECT id FROM exam_question_snapshot WHERE exam_version_id=:exam ORDER BY position")
                .param("exam", examId).query(UUID.class).list();
    }

    Header insert(UUID id, UUID assignmentId, UUID examId, UUID studentId, int number, Instant startedAt,
                  Instant deadline, List<UUID> questionIds) {
        jdbc.sql("INSERT INTO attempt(id,assignment_id,exam_version_id,student_id,attempt_number,state,started_at,deadline,finalized_at) "
                        + "VALUES (:id,:assignment,:exam,:student,:number,'IN_PROGRESS',:started,:deadline,NULL)")
                .param("id", id).param("assignment", assignmentId).param("exam", examId).param("student", studentId)
                .param("number", number).param("started", Timestamp.from(startedAt)).param("deadline", Timestamp.from(deadline)).update();
        int position = 0;
        for (UUID questionId : questionIds) {
            jdbc.sql("INSERT INTO attempt_question(attempt_id,exam_question_id,position) VALUES (:attempt,:question,:position)")
                    .param("attempt", id).param("question", questionId).param("position", position++).update();
        }
        return new Header(id, assignmentId, examId, studentId, number, AttemptState.IN_PROGRESS, startedAt, deadline, null);
    }

    AttemptView view(Header header) {
        var questions = new LinkedHashMap<UUID, QuestionBuilder>();
        var rows = jdbc.sql("SELECT q.exam_question_id,s.prompt,o.id AS option_id,o.label,o.option_text,a.selected_option_id,COALESCE(a.revision,0) AS revision "
                        + "FROM attempt_question q JOIN exam_question_snapshot s ON s.id=q.exam_question_id "
                        + "LEFT JOIN exam_option_snapshot o ON o.exam_question_id=s.id "
                        + "LEFT JOIN answer a ON a.attempt_id=q.attempt_id AND a.exam_question_id=q.exam_question_id "
                        + "WHERE q.attempt_id=:attempt ORDER BY q.position,o.position")
                .param("attempt", header.id()).query((rs, row) -> new Object[]{
                        rs.getObject("exam_question_id", UUID.class), rs.getString("prompt"),
                        rs.getObject("option_id", UUID.class), rs.getString("label"), rs.getString("option_text"),
                        rs.getObject("selected_option_id", UUID.class), rs.getInt("revision")}).list();
        for (Object[] row : rows) {
            UUID questionId = (UUID) row[0];
            var question = questions.computeIfAbsent(questionId, ignored -> new QuestionBuilder(questionId, (String) row[1]));
            UUID optionId = (UUID) row[2];
            if (optionId != null) question.options.add(new AttemptView.Option(optionId, (String) row[3], (String) row[4]));
            question.selectedOptionId = (UUID) row[5];
            question.revision = (Integer) row[6];
        }
        return new AttemptView(header.id(), header.examId(), header.attemptNumber(), header.state(),
                header.startedAt(), header.deadline(), header.finalizedAt(), questions.values().stream().map(QuestionBuilder::build).toList());
    }

    private static Header header(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        var finalized = rs.getTimestamp("finalized_at");
        return new Header(rs.getObject("id", UUID.class), rs.getObject("assignment_id", UUID.class),
                rs.getObject("exam_version_id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getInt("attempt_number"), AttemptState.valueOf(rs.getString("state")),
                rs.getTimestamp("started_at").toInstant(), rs.getTimestamp("deadline").toInstant(),
                finalized == null ? null : finalized.toInstant());
    }
}
