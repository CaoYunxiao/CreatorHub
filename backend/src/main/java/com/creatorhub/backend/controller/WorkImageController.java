package com.creatorhub.backend.controller;

import java.net.URI;
import java.security.Principal;
import java.util.List;

import com.creatorhub.backend.dto.WorkImageResponse;
import com.creatorhub.backend.service.WorkImageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/works/{workId}/images")
public class WorkImageController {
    private final WorkImageService service;

    public WorkImageController(WorkImageService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WorkImageResponse> upload(@PathVariable long workId, Principal principal,
                                                     @RequestParam("file") MultipartFile file) {
        var image = service.upload(workId, principal.getName(), file);
        return ResponseEntity.created(URI.create(image.imageUrl())).body(image);
    }

    @GetMapping
    public List<WorkImageResponse> list(@PathVariable long workId) {
        return service.list(workId);
    }

    @GetMapping("/{fileName}")
    public ResponseEntity<byte[]> read(@PathVariable long workId, @PathVariable String fileName) {
        var image = service.read(workId, fileName);
        return ResponseEntity.ok().contentType(image.contentType())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }
}
