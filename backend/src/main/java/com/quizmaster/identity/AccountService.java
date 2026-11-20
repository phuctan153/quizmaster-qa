package com.quizmaster.identity;

import com.quizmaster.shared.ApiException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository repository;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String dummyHash;

    public AccountService(AccountRepository repository, PasswordEncoder encoder, Clock clock) {
        this.repository = repository;
        this.encoder = encoder;
        this.clock = clock;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    AccountView authenticate(AccountRequests.Login request) {
        var account = repository.byEmail(request.email());
        boolean matches = encoder.matches(request.password(), account.map(Account::passwordHash).orElse(dummyHash));
        if (!matches || account.isEmpty() || !account.get().active()) {
            throw unauthenticated();
        }
        return account.get().view();
    }

    public AccountView activeIdentity(UUID id) {
        return repository.byId(id).filter(Account::active).map(Account::view)
                .orElseThrow(AccountService::unauthenticated);
    }

    @Transactional
    public AccountView create(AccountRequests.Create request) {
        var account = new Account(UUID.randomUUID(), request.email(), encoder.encode(request.password()),
                request.role(), request.active());
        try {
            repository.insert(account, clock.instant());
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "Email is already registered");
        }
        return account.view();
    }

    @Transactional(readOnly = true)
    Page list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid pagination");
        }
        return new Page(repository.list(page, size), page, size, repository.count());
    }

    @Transactional
    AccountView setStatus(UUID id, boolean active) {
        return repository.setStatus(id, active).map(Account::view)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"));
    }

    public static ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required");
    }

    record Page(List<AccountView> items, int page, int size, long total) {}
}
