package ru.murad.yourmarket.service.impl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.murad.yourmarket.config.TelegramProperties;
import ru.murad.yourmarket.service.AdminAccessService;
@Service @RequiredArgsConstructor
public class AdminAccessServiceImpl implements AdminAccessService {
    private final TelegramProperties properties;
    public boolean isAdmin(Long id) { return id != null && properties.admin().userIds().contains(id); }
}
