package com.quizmaster.exams;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ExamRepository {
    private record Header(UUID id, UUID familyId, int versionNumber, UUID ownerId, String title,
                          ExamState state, int durationSeconds, int attemptLimit, BigDecimal passingThreshold,
                          boolean shuffle, int revision) {}
    private record SnapshotHeader(UUID id, UUID sourceQuestionId, int position, String prompt, String difficulty) {}
    private record SnapshotRow(UUID id, UUID sourceQuestionId, int position, String prompt, String difficulty,
                               UUID optionId, String label, String text, Boolean correct) {}

    private static final String HEADER_SQL = "SELECT id,family_id,version_number,owner_id,title,state,duration_seconds,attempt_limit,passing_threshold,shuffle,revision FROM exam_version";
    private static final RowMapper<Header> HEADER = (rs, row) -> new Header(
            rs.getObject("id", UUID.class), rs.getObject("family_id", UUID.class), rs.getInt("version_number"),
            rs.getObject("owner_id", UUID.class), rs.getString("title"), ExamState.valueOf(rs.getString("state")),
            rs.getInt("duration_seconds"), rs.getInt("attempt_limit"), rs.getBigDecimal("passing_threshold"),
            rs.getBoolean("shuffle"), rs.getInt("revision"));

    private final JdbcClient jdbc;
    private final com.quizmaster.questions.QuestionRepository questions;

    public ExamRepository(JdbcClient jdbc, com.quizmaster.questions.QuestionRepository questions) {
        this.jdbc = jdbc;
        this.questions = questions;
    }

    void insertDraft(Exam exam, Instant now) {
        jdbc.sql("INSERT INTO exam_version (id,family_id,version_number,owner_id,title,state,duration_seconds,attempt_limit,passing_threshold,shuffle,revision,created_at,updated_at,published_at) "
                        + "VALUES (:id,:family,1,:owner,:title,'DRAFT',:duration,:attempts,:threshold,:shuffle,0,:created,:updated,NULL)")
                .param("id", exam.id()).param("family", exam.familyId()).param("owner", exam.ownerId())
                .param("title", exam.title()).param("duration", exam.durationSeconds()).param("attempts", exam.attemptLimit())
                .param("threshold", exam.passingThreshold()).param("shuffle", exam.shuffle())
                .param("created", Timestamp.from(now)).param("updated", Timestamp.from(now)).update();
        insertDraftQuestionIds(exam.id(), exam.questionIds());
    }

    Optional<Exam> byId(UUID id, UUID ownerId) {
        var header = jdbc.sql(HEADER_SQL + " WHERE id=:id AND owner_id=:owner")
                .param("id", id).param("owner", ownerId).query(HEADER).optional();
        return header.map(this::hydrate);
    }

    boolean owned(UUID id, UUID ownerId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM exam_version WHERE id=:id AND owner_id=:owner)")
                .param("id", id).param("owner", ownerId).query(Boolean.class).single();
    }

    List<Exam> list(UUID ownerId, int page, int size) {
        var headers = jdbc.sql(HEADER_SQL + " WHERE owner_id=:owner ORDER BY id LIMIT :size OFFSET :offset")
                .param("owner", ownerId).param("size", size).param("offset", (long) page * size).query(HEADER).list();
        return headers.stream().map(this::hydrate).toList();
    }

    long count(UUID ownerId) {
        return jdbc.sql("SELECT count(*) FROM exam_version WHERE owner_id=:owner").param("owner", ownerId).query(Long.class).single();
    }

    boolean updateDraft(UUID id, UUID ownerId, ExamRequests.Update request, Instant now) {
        int changed = jdbc.sql("UPDATE exam_version SET title=:title,duration_seconds=:duration,attempt_limit=:attempts,passing_threshold=:threshold,shuffle=:shuffle,revision=revision+1,updated_at=:updated "
                        + "WHERE id=:id AND owner_id=:owner AND state='DRAFT' AND revision=:revision")
                .param("title", request.title()).param("duration", request.durationSeconds().intValueExact()).param("attempts", request.attemptLimit().intValueExact())
                .param("threshold", request.passingThreshold()).param("shuffle", request.shuffle()).param("updated", Timestamp.from(now))
                .param("id", id).param("owner", ownerId).param("revision", request.revision()).update();
        if (changed == 0) return false;
        jdbc.sql("DELETE FROM exam_draft_question WHERE exam_version_id=:id").param("id", id).update();
        insertDraftQuestionIds(id, request.questionIds());
        return true;
    }

    boolean publish(UUID id, UUID ownerId, int revision, List<com.quizmaster.questions.Question> questions, Instant now) {
        int changed = jdbc.sql("UPDATE exam_version SET state='PUBLISHED',revision=revision+1,published_at=:now,updated_at=:now "
                        + "WHERE id=:id AND owner_id=:owner AND state='DRAFT' AND revision=:revision")
                .param("now", Timestamp.from(now)).param("id", id).param("owner", ownerId).param("revision", revision).update();
        if (changed == 0) return false;
        int questionPosition = 0;
        for (var question : questions) {
            UUID snapshotId = UUID.randomUUID();
            jdbc.sql("INSERT INTO exam_question_snapshot (id,exam_version_id,source_question_id,position,prompt,difficulty) VALUES (:id,:exam,:source,:position,:prompt,:difficulty)")
                    .param("id", snapshotId).param("exam", id).param("source", question.id()).param("position", questionPosition++)
                    .param("prompt", question.prompt()).param("difficulty", question.difficulty() == null ? null : question.difficulty().name()).update();
            int optionPosition = 0;
            for (var option : question.options()) {
                jdbc.sql("INSERT INTO exam_option_snapshot (id,exam_question_id,source_option_id,position,label,option_text,correct) VALUES (:id,:question,:source,:position,:label,:text,:correct)")
                        .param("id", UUID.randomUUID()).param("question", snapshotId).param("source", option.id())
                        .param("position", optionPosition++).param("label", option.label()).param("text", option.text())
                        .param("correct", option.correct()).update();
            }
        }
        return true;
    }

    private void insertDraftQuestionIds(UUID examId, List<UUID> questionIds) {
        for (int position = 0; position < questionIds.size(); position++) {
            jdbc.sql("INSERT INTO exam_draft_question (exam_version_id,question_id,position) VALUES (:exam,:question,:position)")
                    .param("exam", examId).param("question", questionIds.get(position)).param("position", position).update();
        }
    }

    private Exam hydrate(Header header) {
        var ids = header.state() == ExamState.DRAFT
                ? jdbc.sql("SELECT question_id FROM exam_draft_question WHERE exam_version_id=:id ORDER BY position").param("id", header.id()).query(UUID.class).list()
                : jdbc.sql("SELECT source_question_id FROM exam_question_snapshot WHERE exam_version_id=:id ORDER BY position").param("id", header.id()).query(UUID.class).list();
        var snapshots = header.state() == ExamState.DRAFT ? List.<Exam.QuestionSnapshot>of() : snapshots(header.id());
        return new Exam(header.id(), header.familyId(), header.versionNumber(), header.ownerId(), header.title(), header.state(),
                header.durationSeconds(), header.attemptLimit(), header.passingThreshold(), header.shuffle(), header.revision(), ids, snapshots);
    }

    private List<Exam.QuestionSnapshot> snapshots(UUID examId) {
        var rows = jdbc.sql("SELECT q.id,q.source_question_id,q.position,q.prompt,q.difficulty,o.id AS option_id,o.label,o.option_text,o.correct "
                        + "FROM exam_question_snapshot q LEFT JOIN exam_option_snapshot o ON o.exam_question_id=q.id "
                        + "WHERE q.exam_version_id=:exam ORDER BY q.position,o.position")
                .param("exam", examId).query((rs, row) -> new SnapshotRow(
                        rs.getObject("id", UUID.class), rs.getObject("source_question_id", UUID.class), rs.getInt("position"),
                        rs.getString("prompt"), rs.getString("difficulty"), rs.getObject("option_id", UUID.class),
                        rs.getString("label"), rs.getString("option_text"), (Boolean) rs.getObject("correct"))).list();
        var headers = new LinkedHashMap<UUID, SnapshotHeader>();
        var options = new LinkedHashMap<UUID, List<Exam.OptionSnapshot>>();
        for (var row : rows) {
            headers.putIfAbsent(row.id(), new SnapshotHeader(row.id(), row.sourceQuestionId(), row.position(), row.prompt(), row.difficulty()));
            if (row.optionId() != null) options.computeIfAbsent(row.id(), ignored -> new ArrayList<>())
                    .add(new Exam.OptionSnapshot(row.optionId(), row.label(), row.text(), row.correct()));
        }
        return headers.values().stream().map(header -> new Exam.QuestionSnapshot(header.id(), header.sourceQuestionId(),
                header.position(), header.prompt(), header.difficulty(), options.getOrDefault(header.id(), List.of()))).toList();
    }
}
