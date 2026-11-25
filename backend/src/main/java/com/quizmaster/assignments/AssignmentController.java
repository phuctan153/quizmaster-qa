package com.quizmaster.assignments;

import com.quizmaster.identity.AccountView;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class AssignmentController {
    private final AssignmentService service;

    public AssignmentController(AssignmentService service) { this.service = service; }

    @GetMapping("/students")
    AssignmentPage<StudentSummary> students(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.students(page, size);
    }

    @PutMapping("/exams/{examId}/assignments/{studentId}")
    ResponseEntity<Assignment> assign(@PathVariable UUID examId, @PathVariable UUID studentId,
                                      @AuthenticationPrincipal AccountView actor) {
        var result = service.assign(examId, actor.id(), studentId);
        if (!result.created()) return ResponseEntity.ok(result.assignment());
        return ResponseEntity.created(URI.create("/api/v1/exams/" + examId + "/assignments/" + studentId)).body(result.assignment());
    }

    @GetMapping("/exams/{examId}/assignments")
    AssignmentPage<Assignment> roster(@PathVariable UUID examId, @AuthenticationPrincipal AccountView actor,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.roster(examId, actor.id(), page, size);
    }

    @GetMapping("/assignments")
    AssignmentPage<AssignedExam> assigned(@AuthenticationPrincipal AccountView actor,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.assigned(actor.id(), page, size);
    }
}
