package com.creatorhub.backend.dto;

import java.util.List;

public record WorkCreateRequest(String title, String intro, String content, List<Long> tagIds) {
}
