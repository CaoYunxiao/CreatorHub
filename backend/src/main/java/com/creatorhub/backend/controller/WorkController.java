package com.creatorhub.backend.controller;

import java.net.URI;
import java.security.Principal;

import com.creatorhub.backend.dto.WorkDetailResponse;
import com.creatorhub.backend.dto.WorkPageResponse;
import com.creatorhub.backend.dto.WorkWriteRequest;
import com.creatorhub.backend.service.WorkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/works")
public class WorkController {
    private final WorkService workService;

    public WorkController(WorkService workService) {
        this.workService = workService;
    }

    @GetMapping
    public WorkPageResponse listWorks(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return workService.listWorks(page, size);
    }

    @GetMapping("/{id}")
    public WorkDetailResponse getWork(@PathVariable long id) {
        return workService.getWork(id);
    }

    @PostMapping
    public ResponseEntity<Void> createWork(Principal principal, @RequestBody WorkWriteRequest request) {
        long id = workService.createWork(principal.getName(), request);
        return ResponseEntity.created(URI.create("/api/works/" + id)).build();
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateWork(@PathVariable long id, Principal principal, @RequestBody WorkWriteRequest request) {
        workService.updateWork(id, principal.getName(), request);
    }
}
