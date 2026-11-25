package com.quizmaster.exams;

import com.quizmaster.identity.AccountView;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/exams")
public class ExamController {
    private final ExamService service;

    public ExamController(ExamService service) { this.service = service; }

    @GetMapping
    ExamPage list(@AuthenticationPrincipal AccountView actor,
                  @RequestParam(defaultValue = "0") int page,
                  @RequestParam(defaultValue = "20") int size) {
        return service.list(actor.id(), page, size);
    }

    @PostMapping
    ResponseEntity<Exam> create(@AuthenticationPrincipal AccountView actor, @Valid @RequestBody ExamRequests.Input request) {
        var exam = service.create(actor.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/exams/" + exam.id())).body(exam);
    }

    @GetMapping("/{id}")
    Exam get(@PathVariable UUID id, @AuthenticationPrincipal AccountView actor) {
        return service.read(id, actor.id());
    }

    @PutMapping("/{id}")
    Exam update(@PathVariable UUID id, @AuthenticationPrincipal AccountView actor,
                @Valid @RequestBody ExamRequests.Update request) {
        return service.update(id, actor.id(), request);
    }

    @PostMapping("/{id}/publish")
    Exam publish(@PathVariable UUID id, @AuthenticationPrincipal AccountView actor,
                 @Valid @RequestBody ExamRequests.Revision request) {
        return service.publish(id, actor.id(), request);
    }

    @PostMapping("/{id}/close")
    Exam close(@PathVariable UUID id, @AuthenticationPrincipal AccountView actor) {
        return service.close(id, actor.id());
    }
}
