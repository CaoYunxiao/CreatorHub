package com.creatorhub.backend.controller;

import java.security.Principal;
import java.util.List;

import com.creatorhub.backend.dto.TagResponse;
import com.creatorhub.backend.service.TagService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TagController {
    private final TagService tags;

    public TagController(TagService tags) {
        this.tags = tags;
    }

    @GetMapping("/api/tags")
    public List<TagResponse> list() {
        return tags.list();
    }

    @PostMapping("/api/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse create(@RequestBody CreateTagRequest request) {
        return tags.create(request.name());
    }

    @GetMapping("/api/works/{workId}/tags")
    public List<TagResponse> listForWork(@PathVariable long workId) {
        return tags.listForWork(workId);
    }

    @PutMapping("/api/works/{workId}/tags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceForWork(@PathVariable long workId, Principal principal, @RequestBody WorkTagsRequest request) {
        tags.replaceForWork(workId, principal.getName(), request.tagIds());
    }

    public record CreateTagRequest(String name) {
    }

    public record WorkTagsRequest(List<Long> tagIds) {
    }
}
