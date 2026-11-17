package com.creatorhub.backend.controller;

import java.security.Principal;

import com.creatorhub.backend.dto.WorkPageResponse;
import com.creatorhub.backend.service.FavoriteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FavoriteController {
    private final FavoriteService favorites;

    public FavoriteController(FavoriteService favorites) {
        this.favorites = favorites;
    }

    @PutMapping("/api/works/{id}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@PathVariable long id, Principal principal) {
        favorites.add(principal.getName(), id);
    }

    @DeleteMapping("/api/works/{id}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long id, Principal principal) {
        favorites.remove(principal.getName(), id);
    }

    @GetMapping("/api/me/favorites")
    public WorkPageResponse list(Principal principal,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return favorites.list(principal.getName(), page, size);
    }
}
