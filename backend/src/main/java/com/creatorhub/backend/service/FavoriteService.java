package com.creatorhub.backend.service;

import com.creatorhub.backend.dto.WorkPageResponse;
import com.creatorhub.backend.exception.InvalidRequestException;
import com.creatorhub.backend.exception.WorkNotFoundException;
import com.creatorhub.backend.repository.FavoriteRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class FavoriteService {
    private final FavoriteRepository favorites;
    private final UserService users;

    public FavoriteService(FavoriteRepository favorites, UserService users) {
        this.favorites = favorites;
        this.users = users;
    }

    public void add(String username, long workId) {
        validateWorkId(workId);
        long userId = users.currentUser(username).id();
        try {
            if (favorites.insert(userId, workId) == 0) {
                throw new WorkNotFoundException();
            }
        } catch (DuplicateKeyException ignored) {
            // 唯一约束处理重复请求，已有收藏时间保持不变。
        }
    }

    public void remove(String username, long workId) {
        validateWorkId(workId);
        favorites.delete(users.currentUser(username).id(), workId);
    }

    public WorkPageResponse list(String username, int page, int size) {
        if (page < 1) {
            throw new InvalidRequestException("page 必须大于等于 1");
        }
        if (size < 1 || size > 100) {
            throw new InvalidRequestException("size 必须在 1 到 100 之间");
        }
        long userId = users.currentUser(username).id();
        return new WorkPageResponse(page, size, favorites.findPage(userId, size, (page - 1L) * size));
    }

    private static void validateWorkId(long workId) {
        if (workId < 1) {
            throw new InvalidRequestException("作品 ID 必须大于等于 1");
        }
    }
}
