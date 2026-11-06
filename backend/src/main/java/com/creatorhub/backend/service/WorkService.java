package com.creatorhub.backend.service;

import com.creatorhub.backend.dto.WorkPageResponse;
import com.creatorhub.backend.dto.WorkSummaryResponse;
import com.creatorhub.backend.exception.InvalidRequestException;
import com.creatorhub.backend.repository.WorkRepository;
import org.springframework.stereotype.Service;

@Service
public class WorkService {
    private final WorkRepository workRepository;

    public WorkService(WorkRepository workRepository) {
        this.workRepository = workRepository;
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
}
