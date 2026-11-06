package com.creatorhub.backend.entity;

/** 仅在认证内部使用；不得直接作为接口响应。 */
public record UserAccount(long id, String username, String passwordHash) {
    @Override
    public String toString() {
        return "UserAccount[id=" + id + ", credentials=REDACTED]";
    }
}
