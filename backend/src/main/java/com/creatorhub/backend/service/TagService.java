package com.creatorhub.backend.service;

import java.util.HashSet;
import java.util.List;

import com.creatorhub.backend.dto.TagResponse;
import com.creatorhub.backend.exception.InvalidRequestException;
import com.creatorhub.backend.exception.TagNameTakenException;
import com.creatorhub.backend.exception.WorkForbiddenException;
import com.creatorhub.backend.exception.WorkNotFoundException;
import com.creatorhub.backend.repository.TagRepository;
import com.creatorhub.backend.repository.WorkRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {
    private final TagRepository tags;
    private final WorkRepository works;
    private final UserService users;

    public TagService(TagRepository tags, WorkRepository works, UserService users) {
        this.tags = tags;
        this.works = works;
        this.users = users;
    }

    public List<TagResponse> list() {
        return tags.list();
    }

    public TagResponse create(String input) {
        String name = input == null ? "" : input.strip();
        if (name.isBlank() || name.codePointCount(0, name.length()) > 50) {
            throw new InvalidRequestException("标签名称必须包含 1 到 50 个字符");
        }
        try {
            return new TagResponse(tags.insert(name), name);
        } catch (DuplicateKeyException exception) {
            throw new TagNameTakenException();
        }
    }

    public List<TagResponse> listForWork(long workId) {
        validateWorkId(workId);
        works.findAuthorId(workId).orElseThrow(WorkNotFoundException::new);
        return tags.findByWorkId(workId);
    }

    @Transactional
    public void replaceForWork(long workId, String username, List<Long> tagIds) {
        validateWorkId(workId);
        if (tagIds == null || tagIds.size() > 50 || tagIds.stream().anyMatch(id -> id == null || id < 1)
                || new HashSet<>(tagIds).size() != tagIds.size()) {
            throw new InvalidRequestException("tagIds 必须是最多 50 个不重复的正整数，可传空数组");
        }
        long userId = users.currentUser(username).id();
        // 锁定作品行，串行执行同一作品的替换事务。
        long authorId = works.lockAuthorId(workId).orElseThrow(WorkNotFoundException::new);
        if (authorId != userId) {
            throw new WorkForbiddenException();
        }
        if (!tags.allExist(tagIds)) {
            throw new InvalidRequestException("至少有一个标签不存在");
        }
        tags.replaceForWork(workId, tagIds);
    }

    private static void validateWorkId(long workId) {
        if (workId < 1) {
            throw new InvalidRequestException("作品 ID 必须大于等于 1");
        }
    }
}
