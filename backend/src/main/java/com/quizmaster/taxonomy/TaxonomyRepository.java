package com.quizmaster.taxonomy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TaxonomyRepository {
    private static final RowMapper<Category> CATEGORY = (rs, row) -> new Category(rs.getObject("id", UUID.class), rs.getString("name"));
    private static final RowMapper<Subject> SUBJECT = (rs, row) -> new Subject(rs.getObject("id", UUID.class), rs.getObject("category_id", UUID.class), rs.getString("name"));
    private final JdbcClient jdbc;
    public TaxonomyRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    void insertCategory(UUID id, String name, Instant createdAt) {
        jdbc.sql("INSERT INTO category (id,name,normalized_name,created_at) VALUES (:id,:name,:normalized,:created)")
                .param("id", id).param("name", name).param("normalized", TaxonomyRequests.normalize(name))
                .param("created", Timestamp.from(createdAt)).update();
    }
    void insertSubject(UUID id, UUID categoryId, String name, Instant createdAt) {
        jdbc.sql("INSERT INTO subject (id,category_id,name,normalized_name,created_at) VALUES (:id,:category,:name,:normalized,:created)")
                .param("id", id).param("category", categoryId).param("name", name)
                .param("normalized", TaxonomyRequests.normalize(name)).param("created", Timestamp.from(createdAt)).update();
    }
    boolean categoryExists(UUID id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM category WHERE id=:id)").param("id", id).query(Boolean.class).single();
    }
    Optional<Category> category(UUID id) {
        return jdbc.sql("SELECT id,name FROM category WHERE id=:id").param("id", id).query(CATEGORY).optional();
    }
    Optional<Subject> subject(UUID id) {
        return jdbc.sql("SELECT id,category_id,name FROM subject WHERE id=:id").param("id", id).query(SUBJECT).optional();
    }
    List<Category> categories(int page, int size) {
        return jdbc.sql("SELECT id,name FROM category ORDER BY id LIMIT :size OFFSET :offset")
                .param("size", size).param("offset", (long) page * size).query(CATEGORY).list();
    }
    List<Subject> subjects(UUID categoryId, int page, int size) {
        var query = categoryId == null
                ? jdbc.sql("SELECT id,category_id,name FROM subject ORDER BY id LIMIT :size OFFSET :offset")
                : jdbc.sql("SELECT id,category_id,name FROM subject WHERE category_id=:category ORDER BY id LIMIT :size OFFSET :offset").param("category", categoryId);
        return query.param("size", size).param("offset", (long) page * size).query(SUBJECT).list();
    }
    long countCategories() { return jdbc.sql("SELECT count(*) FROM category").query(Long.class).single(); }
    long countSubjects(UUID categoryId) {
        var query = categoryId == null ? jdbc.sql("SELECT count(*) FROM subject")
                : jdbc.sql("SELECT count(*) FROM subject WHERE category_id=:category").param("category", categoryId);
        return query.query(Long.class).single();
    }
}
