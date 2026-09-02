package com.mira.items.api;

public record MiraItemInfo(
        String id,
        String displayName,
        String material,
        boolean enabled,
        int issuedCount,
        int limit
) {
    public boolean unlimited() {
        return limit < 0;
    }
}
