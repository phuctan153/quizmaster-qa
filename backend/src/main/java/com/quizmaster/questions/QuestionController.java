package com.quizmaster.questions;

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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {
    private final QuestionService service;

    public QuestionController(QuestionService service) { this.service = service; }

    @GetMapping
    QuestionPage list(@AuthenticationPrincipal AccountView actor,
                      @RequestParam(required = false) UUID subjectId,
                      @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "20") int size) {
        return service.list(actor.id(), subjectId, page, size);
    }

    @PostMapping
    ResponseEntity<Question> create(@AuthenticationPrincipal AccountView actor,
                                    @Valid @RequestBody QuestionRequests.Input request) {
        var question = service.create(actor.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/questions/" + question.id())).body(question);
    }

    @GetMapping("/{id}")
    Question get(@PathVariable UUID id, @AuthenticationPrincipal AccountView actor) {
        return service.get(id, actor.id());
    }

    @PutMapping("/{id}")
    Question update(@PathVariable UUID id, @AuthenticationPrincipal AccountView actor,
                    @Valid @RequestBody QuestionRequests.Update request) {
        return service.update(id, actor.id(), request);
    }

    @PostMapping("/import")
    ResponseEntity<QuestionImportResult> importCsv(@AuthenticationPrincipal AccountView actor,
                                                    @RequestPart("file") MultipartFile file) {
        try {
            return ResponseEntity.status(201).body(service.importCsv(actor.id(), file.getBytes()));
        } catch (java.io.IOException ex) {
            throw new com.quizmaster.shared.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR", "CSV file could not be read");
        }
    }
}
