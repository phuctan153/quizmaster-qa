package com.quizmaster.attempts;

import com.quizmaster.identity.AccountView;
import com.quizmaster.shared.ApiException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AttemptController {
    private final AttemptService service;

    public AttemptController(AttemptService service) { this.service = service; }

    @PostMapping("/exams/{examId}/attempts")
    ResponseEntity<AttemptView> start(@PathVariable UUID examId, @AuthenticationPrincipal AccountView actor) {
        var result = service.start(examId, actor.id());
        if (result.conflictCode() != null) {
            var message = "ATTEMPT_LIMIT".equals(result.conflictCode())
                    ? "Attempt limit has been reached" : "Only a published exam can be started";
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, result.conflictCode(), message);
        }
        if (!result.created()) return ResponseEntity.ok(result.attempt());
        return ResponseEntity.created(URI.create("/api/v1/attempts/" + result.attempt().id())).body(result.attempt());
    }

    @GetMapping("/attempts/{attemptId}")
    AttemptView read(@PathVariable UUID attemptId, @AuthenticationPrincipal AccountView actor) {
        return service.read(attemptId, actor.id());
    }
}
