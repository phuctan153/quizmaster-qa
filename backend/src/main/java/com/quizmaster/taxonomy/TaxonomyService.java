package com.quizmaster.taxonomy;

import com.quizmaster.shared.ApiException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxonomyService {
    private final TaxonomyRepository repository;
    private final Clock clock;
    public TaxonomyService(TaxonomyRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }

    @Transactional
    public Category createCategory(TaxonomyRequests.Name request) {
        var category = new Category(UUID.randomUUID(), request.name());
        try { repository.insertCategory(category.id(), category.name(), clock.instant()); }
        catch (DuplicateKeyException ex) { throw duplicate("Category name is already registered"); }
        return category;
    }

    @Transactional(readOnly = true)
    public TaxonomyPage<Category> categories(int page, int size) {
        validatePage(page, size);
        return new TaxonomyPage<>(repository.categories(page, size), page, size, repository.countCategories());
    }

    @Transactional
    public Subject createSubject(TaxonomyRequests.SubjectInput request) {
        if (!repository.categoryExists(request.categoryId())) throw notFound();
        var subject = new Subject(UUID.randomUUID(), request.categoryId(), request.name());
        try { repository.insertSubject(subject.id(), subject.categoryId(), subject.name(), clock.instant()); }
        catch (DuplicateKeyException ex) { throw duplicate("Subject name is already registered for this category"); }
        return subject;
    }

    @Transactional(readOnly = true)
    public TaxonomyPage<Subject> subjects(UUID categoryId, int page, int size) {
        validatePage(page, size);
        if (categoryId != null && !repository.categoryExists(categoryId)) throw notFound();
        return new TaxonomyPage<>(repository.subjects(categoryId, page, size), page, size, repository.countSubjects(categoryId));
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid pagination");
    }
    private static ApiException duplicate(String message) { return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_NAME", message); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"); }
}
