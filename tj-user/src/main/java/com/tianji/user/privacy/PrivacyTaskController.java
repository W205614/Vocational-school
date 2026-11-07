package com.tianji.user.privacy;

import com.tianji.user.service.impl.AccountPrivacyService;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/account-privacy/tasks")
public class PrivacyTaskController {
    public record Create(String requestId) {}

    public record Decision(long version, String planHash) {}

    public record Password(String password) {
        @Override
        public String toString() {
            return "Password[REDACTED]";
        }
    }

    private final PrivacyTaskService tasks;
    private final AccountPrivacyService privacy;

    public PrivacyTaskController(PrivacyTaskService tasks, AccountPrivacyService privacy) {
        this.tasks = tasks;
        this.privacy = privacy;
    }

    @PostMapping
    public Map<String, Object> create(
            @RequestBody Create form, @RequestHeader("Idempotency-Key") String key) {
        return tasks.create(form.requestId(), key);
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return tasks.list(page, size);
    }

    @GetMapping("/{id}")
    public Map<String, Object> view(@PathVariable String id) {
        return tasks.view(id);
    }

    @PostMapping("/{id}/approve")
    public void approve(@PathVariable String id, @RequestBody Decision form) {
        tasks.approve(id, form.version(), form.planHash());
    }

    @PostMapping("/{id}/retry")
    public void retry(@PathVariable String id, @RequestBody Decision form) {
        tasks.retry(id, form.version());
    }

    @PostMapping("/{id}/download")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/zip",
                            schema =
                                    @io.swagger.v3.oas.annotations.media.Schema(
                                            type = "string",
                                            format = "binary")))
    public void download(
            @PathVariable String id, @RequestBody Password form, HttpServletResponse response)
            throws IOException {
        privacy.verify(form.password());
        try (var input = tasks.download(id)) {
            response.setContentType("application/zip");
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader(
                    "Content-Disposition", "attachment; filename=\"privacy-" + id + ".zip\"");
            input.transferTo(response.getOutputStream());
        }
    }
}
