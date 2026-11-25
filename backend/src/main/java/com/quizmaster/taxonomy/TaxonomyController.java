package com.quizmaster.taxonomy;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class TaxonomyController {
    private final TaxonomyService service;
    public TaxonomyController(TaxonomyService service) { this.service = service; }

    @GetMapping("/categories")
    TaxonomyPage<Category> categories(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return service.categories(page, size);
    }

    @PostMapping("/categories")
    ResponseEntity<Category> createCategory(@Valid @RequestBody TaxonomyRequests.Name request) {
        var category = service.createCategory(request);
        return ResponseEntity.created(URI.create("/api/v1/categories/" + category.id())).body(category);
    }

    @GetMapping("/subjects")
    TaxonomyPage<Subject> subjects(@RequestParam(required = false) UUID categoryId,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return service.subjects(categoryId, page, size);
    }

    @PostMapping("/subjects")
    ResponseEntity<Subject> createSubject(@Valid @RequestBody TaxonomyRequests.SubjectInput request) {
        var subject = service.createSubject(request);
        return ResponseEntity.created(URI.create("/api/v1/subjects/" + subject.id())).body(subject);
    }
}
