package com.quizmaster.questions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionRepository {
    private record Header(UUID id, UUID ownerId, UUID subjectId, String prompt, Difficulty difficulty, int revision) {}

    private static final RowMapper<Header> HEADER = (rs, row) -> new Header(
            rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class),
            rs.getObject("subject_id", UUID.class), rs.getString("prompt"),
            rs.getString("difficulty") == null ? null : Difficulty.valueOf(rs.getString("difficulty")),
            rs.getInt("revision"));
    private static final RowMapper<Question.Option> OPTION = (rs, row) -> new Question.Option(
            rs.getObject("id", UUID.class), rs.getString("label"), rs.getString("option_text"), rs.getBoolean("correct"));

    private final JdbcClient jdbc;

    public QuestionRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    boolean subjectExists(UUID id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM subject WHERE id=:id)").param("id", id).query(Boolean.class).single();
    }

    boolean owned(UUID id, UUID ownerId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM question WHERE id=:id AND owner_id=:owner)")
                .param("id", id).param("owner", ownerId).query(Boolean.class).single();
    }

    void insert(Question question, Instant now) {
        jdbc.sql("INSERT INTO question (id,owner_id,subject_id,prompt,difficulty,revision,created_at,updated_at) "
                        + "VALUES (:id,:owner,:subject,:prompt,:difficulty,0,:created,:updated)")
                .param("id", question.id()).param("owner", question.ownerId()).param("subject", question.subjectId())
                .param("prompt", question.prompt()).param("difficulty", question.difficulty() == null ? null : question.difficulty().name())
                .param("created", Timestamp.from(now)).param("updated", Timestamp.from(now)).update();
        insertOptions(question.id(), question.options());
    }

    List<Question> list(UUID ownerId, UUID subjectId, int page, int size) {
        var query = subjectId == null
                ? jdbc.sql("SELECT id,owner_id,subject_id,prompt,difficulty,revision FROM question WHERE owner_id=:owner ORDER BY id LIMIT :size OFFSET :offset")
                : jdbc.sql("SELECT id,owner_id,subject_id,prompt,difficulty,revision FROM question WHERE owner_id=:owner AND subject_id=:subject ORDER BY id LIMIT :size OFFSET :offset").param("subject", subjectId);
        var headers = query.param("owner", ownerId).param("size", size).param("offset", (long) page * size).query(HEADER).list();
        return hydrate(headers);
    }

    long count(UUID ownerId, UUID subjectId) {
        var query = subjectId == null
                ? jdbc.sql("SELECT count(*) FROM question WHERE owner_id=:owner")
                : jdbc.sql("SELECT count(*) FROM question WHERE owner_id=:owner AND subject_id=:subject").param("subject", subjectId);
        return query.param("owner", ownerId).query(Long.class).single();
    }

    public java.util.Optional<Question> byId(UUID id, UUID ownerId) {
        var headers = jdbc.sql("SELECT id,owner_id,subject_id,prompt,difficulty,revision FROM question WHERE id=:id AND owner_id=:owner")
                .param("id", id).param("owner", ownerId).query(HEADER).list();
        return hydrate(headers).stream().findFirst();
    }

    boolean update(UUID id, UUID ownerId, QuestionRequests.Update request, Instant now) {
        int changed = jdbc.sql("UPDATE question SET subject_id=:subject,prompt=:prompt,difficulty=:difficulty,revision=revision+1,updated_at=:updated "
                        + "WHERE id=:id AND owner_id=:owner AND revision=:revision")
                .param("subject", request.subjectId()).param("prompt", request.prompt())
                .param("difficulty", request.difficulty() == null ? null : request.difficulty().name())
                .param("updated", Timestamp.from(now)).param("id", id).param("owner", ownerId)
                .param("revision", request.revision()).update();
        if (changed == 0) return false;
        jdbc.sql("DELETE FROM question_option WHERE question_id=:id").param("id", id).update();
        insertOptions(id, request.options().stream().map(option -> new Question.Option(
                UUID.randomUUID(), option.label(), option.text(), option.correct())).toList());
        return true;
    }

    private void insertOptions(UUID questionId, List<Question.Option> options) {
        for (int position = 0; position < options.size(); position++) {
            var option = options.get(position);
            jdbc.sql("INSERT INTO question_option (id,question_id,position,label,normalized_label,option_text,normalized_text,correct) "
                            + "VALUES (:id,:question,:position,:label,:normalizedLabel,:text,:normalizedText,:correct)")
                    .param("id", option.id()).param("question", questionId).param("position", position)
                    .param("label", option.label()).param("normalizedLabel", normalize(option.label()))
                    .param("text", option.text()).param("normalizedText", normalize(option.text()))
                    .param("correct", option.correct()).update();
        }
    }

    private List<Question> hydrate(List<Header> headers) {
        if (headers.isEmpty()) return List.of();
        var ids = headers.stream().map(Header::id).toList();
        Map<UUID, List<Question.Option>> options = jdbc.sql("SELECT id,question_id,label,option_text,correct FROM question_option "
                        + "WHERE question_id IN (:ids) ORDER BY question_id,position")
                .param("ids", ids).query((rs, row) -> Map.entry(rs.getObject("question_id", UUID.class), OPTION.mapRow(rs, row)))
                .list().stream().collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        return headers.stream().map(header -> new Question(header.id(), header.ownerId(), header.subjectId(),
                header.prompt(), header.difficulty(), header.revision(), options.getOrDefault(header.id(), List.of()))).toList();
    }

    private static String normalize(String value) { return value.trim().toLowerCase(java.util.Locale.ROOT); }
}
