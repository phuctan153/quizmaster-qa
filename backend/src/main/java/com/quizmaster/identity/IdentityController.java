package com.quizmaster.identity;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {
    private final AccountService accounts;
    private final JwtService tokens;

    public IdentityController(AccountService accounts, JwtService tokens) {
        this.accounts = accounts;
        this.tokens = tokens;
    }

    @PostMapping("/auth/login")
    ResponseEntity<JwtService.Token> login(@Valid @RequestBody AccountRequests.Login request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(tokens.issue(accounts.authenticate(request)));
    }

    @GetMapping("/auth/me")
    AccountView me(@AuthenticationPrincipal AccountView account) {
        return account;
    }

    @PostMapping("/users")
    ResponseEntity<AccountView> create(@Valid @RequestBody AccountRequests.Create request) {
        var account = accounts.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + account.id())).body(account);
    }

    @GetMapping("/users")
    AccountService.Page list(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        return accounts.list(page, size);
    }

    @PatchMapping("/users/{id}/status")
    AccountView status(@PathVariable UUID id, @Valid @RequestBody AccountRequests.Status request) {
        return accounts.setStatus(id, request.active());
    }
}
