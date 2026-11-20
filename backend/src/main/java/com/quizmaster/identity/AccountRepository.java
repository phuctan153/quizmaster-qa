package com.quizmaster.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    private static final RowMapper<Account> MAPPER = (rs, row) -> new Account(
            rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash"),
            Role.valueOf(rs.getString("role")), rs.getBoolean("active"));
    private final JdbcClient jdbc;

    public AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Account> byEmail(String email) {
        return jdbc.sql("SELECT * FROM account WHERE email = :email")
                .param("email", email).query(MAPPER).optional();
    }

    Optional<Account> byId(UUID id) {
        return jdbc.sql("SELECT * FROM account WHERE id = :id")
                .param("id", id).query(MAPPER).optional();
    }

    void insert(Account account, Instant createdAt) {
        jdbc.sql("INSERT INTO account (id,email,password_hash,role,active,created_at) VALUES (:id,:email,:hash,:role,:active,:created)")
                .param("id", account.id()).param("email", account.email())
                .param("hash", account.passwordHash()).param("role", account.role().name())
                .param("active", account.active())
                .param("created", java.sql.Timestamp.from(createdAt)).update();
    }

    List<AccountView> list(int page, int size) {
        return jdbc.sql("SELECT * FROM account ORDER BY id LIMIT :size OFFSET :offset")
                .param("size", size).param("offset", (long) page * size)
                .query(MAPPER).list().stream().map(Account::view).toList();
    }

    long count() {
        return jdbc.sql("SELECT count(*) FROM account").query(Long.class).single();
    }

    Optional<Account> setStatus(UUID id, boolean active) {
        return jdbc.sql("UPDATE account SET active = :active WHERE id = :id RETURNING *")
                .param("active", active).param("id", id).query(MAPPER).optional();
    }
}
