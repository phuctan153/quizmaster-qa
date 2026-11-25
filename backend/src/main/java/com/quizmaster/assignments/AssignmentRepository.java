package com.quizmaster.assignments;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AssignmentRepository {
    private static final RowMapper<Assignment> ASSIGNMENT = (rs, row) -> new Assignment(
            rs.getObject("id", UUID.class), rs.getObject("exam_version_id", UUID.class),
            rs.getObject("student_id", UUID.class), rs.getInt("allocated_count"),
            rs.getTimestamp("created_at").toInstant());
    private static final RowMapper<AssignedExam> ASSIGNED_EXAM = (rs, row) -> new AssignedExam(
            rs.getObject("exam_version_id", UUID.class), rs.getString("title"),
            com.quizmaster.exams.ExamState.valueOf(rs.getString("state")), rs.getInt("duration_seconds"),
            rs.getInt("attempt_limit"), rs.getInt("allocated_count"));
    private final JdbcClient jdbc;

    public AssignmentRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    boolean lockPublishedExamExists(UUID examId, UUID ownerId) {
        var rows = jdbc.sql("SELECT id FROM exam_version WHERE id=:exam AND owner_id=:owner AND state='PUBLISHED' FOR UPDATE")
                .param("exam", examId).param("owner", ownerId).query(UUID.class).list();
        return !rows.isEmpty();
    }

    Optional<StudentSummary> student(UUID id) {
        return jdbc.sql("SELECT id,email FROM account WHERE id=:id AND role='STUDENT' AND active=true FOR UPDATE")
                .param("id", id).query((rs, row) -> new StudentSummary(rs.getObject("id", UUID.class), rs.getString("email"))).optional();
    }

    boolean accountExists(UUID id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM account WHERE id=:id)").param("id", id).query(Boolean.class).single();
    }

    boolean ownedExamExists(UUID examId, UUID ownerId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM exam_version WHERE id=:exam AND owner_id=:owner)")
                .param("exam", examId).param("owner", ownerId).query(Boolean.class).single();
    }

    boolean assignmentExists(UUID examId, UUID studentId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM exam_assignment WHERE exam_version_id=:exam AND student_id=:student)")
                .param("exam", examId).param("student", studentId).query(Boolean.class).single();
    }

    Assignment assign(UUID examId, UUID studentId, Instant now) {
        jdbc.sql("INSERT INTO exam_assignment(id,exam_version_id,student_id,created_at) VALUES (:id,:exam,:student,:created) ON CONFLICT (exam_version_id,student_id) DO NOTHING")
                .param("id", UUID.randomUUID()).param("exam", examId).param("student", studentId)
                .param("created", Timestamp.from(now)).update();
        return jdbc.sql("SELECT * FROM exam_assignment WHERE exam_version_id=:exam AND student_id=:student")
                .param("exam", examId).param("student", studentId).query(ASSIGNMENT).single();
    }

    List<Assignment> roster(UUID examId, int page, int size) {
        return jdbc.sql("SELECT * FROM exam_assignment WHERE exam_version_id=:exam ORDER BY student_id LIMIT :size OFFSET :offset")
                .param("exam", examId).param("size", size).param("offset", (long) page * size).query(ASSIGNMENT).list();
    }

    long rosterCount(UUID examId) {
        return jdbc.sql("SELECT count(*) FROM exam_assignment WHERE exam_version_id=:exam").param("exam", examId).query(Long.class).single();
    }

    List<AssignedExam> forStudent(UUID studentId, int page, int size) {
        return jdbc.sql("SELECT e.id AS exam_version_id,e.title,e.state,e.duration_seconds,e.attempt_limit,a.allocated_count "
                        + "FROM exam_assignment a JOIN exam_version e ON e.id=a.exam_version_id WHERE a.student_id=:student "
                        + "ORDER BY a.created_at,a.id LIMIT :size OFFSET :offset")
                .param("student", studentId).param("size", size).param("offset", (long) page * size).query(ASSIGNED_EXAM).list();
    }

    long studentCount(UUID studentId) {
        return jdbc.sql("SELECT count(*) FROM exam_assignment WHERE student_id=:student").param("student", studentId).query(Long.class).single();
    }

    List<StudentSummary> activeStudents(int page, int size) {
        return jdbc.sql("SELECT id,email FROM account WHERE role='STUDENT' AND active=true ORDER BY id LIMIT :size OFFSET :offset")
                .param("size", size).param("offset", (long) page * size)
                .query((rs, row) -> new StudentSummary(rs.getObject("id", UUID.class), rs.getString("email"))).list();
    }

    long activeStudentCount() {
        return jdbc.sql("SELECT count(*) FROM account WHERE role='STUDENT' AND active=true").query(Long.class).single();
    }
}
