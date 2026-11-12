package com.creatorhub.backend.service;

import java.nio.charset.StandardCharsets;

import com.creatorhub.backend.dto.WorkDetailResponse;
import com.creatorhub.backend.dto.WorkPageResponse;
import com.creatorhub.backend.dto.WorkSummaryResponse;
import com.creatorhub.backend.dto.WorkWriteRequest;
import com.creatorhub.backend.exception.InvalidRequestException;
import com.creatorhub.backend.exception.WorkForbiddenException;
import com.creatorhub.backend.exception.WorkNotFoundException;
import com.creatorhub.backend.repository.WorkRepository;
import org.springframework.stereotype.Service;

@Service
public class WorkService {
    private final WorkRepository workRepository;
    private final UserService userService;

    public WorkService(WorkRepository workRepository, UserService userService) {
        this.workRepository = workRepository;
        this.userService = userService;
    }

    public WorkPageResponse listWorks(int page, int size) {
        if (page < 1) {
            throw new InvalidRequestException("page 必须大于等于 1");
        }
        if (size < 1 || size > 100) {
            throw new InvalidRequestException("size 必须在 1 到 100 之间");
        }
        // 先转 long 再乘，避免 int 溢出。
        long offset = (page - 1L) * size;
        var items = workRepository.findPage(size, offset).stream()
                .map(work -> new WorkSummaryResponse(work.id(), work.userId(), work.title(),
                        work.intro(), work.createdAt(), work.updatedAt()))
                .toList();
        return new WorkPageResponse(page, size, items);
    }

    public WorkDetailResponse getWork(long id) {
        validateId(id);
        var work = workRepository.findById(id).orElseThrow(WorkNotFoundException::new);
        return new WorkDetailResponse(work.id(), work.userId(), work.title(), work.intro(),
                work.content(), work.createdAt(), work.updatedAt());
    }

    public long createWork(String username, WorkWriteRequest request) {
        validate(request);
        long authorId = userService.currentUser(username).id();
        return workRepository.insert(authorId, request.title().strip(), request.intro(), request.content());
    }

    public void updateWork(long id, String username, WorkWriteRequest request) {
        validateId(id);
        validate(request);
        long authorId = userService.currentUser(username).id();
        int updated = workRepository.updateByAuthor(id, authorId, request.title().strip(),
                request.intro(), request.content());
        if (updated == 0) {
            var work = workRepository.findById(id).orElseThrow(WorkNotFoundException::new);
            if (work.userId() != authorId) {
                throw new WorkForbiddenException();
            }
            // JDBC 也可能只统计实际发生变化的行。相同内容的 PUT 仍成功。
        }
    }

    private static void validateId(long id) {
        if (id < 1) {
            throw new InvalidRequestException("作品 ID 必须大于等于 1");
        }
    }

    private static void validate(WorkWriteRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new InvalidRequestException("标题不能为空");
        }
        String title = request.title().strip();
        if (title.codePointCount(0, title.length()) > 200) {
            throw new InvalidRequestException("标题不能超过 200 字符");
        }
        String intro = request.intro();
        if (intro != null && intro.codePointCount(0, intro.length()) > 500) {
            throw new InvalidRequestException("简介不能超过 500 字符");
        }
        String content = request.content();
        if (content == null || content.isBlank() || content.length() > 65_535
                || content.getBytes(StandardCharsets.UTF_8).length > 65_535) {
            throw new InvalidRequestException("正文不能为空且 UTF-8 编码不能超过 65535 字节");
        }
    }
}
