package ru.murad.yourmarket.service;
public interface RateLimitService { boolean allow(Long telegramUserId, String actionType); int cleanupExpired(); }
