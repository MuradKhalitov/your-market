package ru.murad.yourmarket.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationConfigurationValidator {
    private final PublicationProperties publication;
    private final TelegramProperties telegram;

    @PostConstruct
    void validate() {
        if (!publication.isModerationEnabled()) return;
        if (telegram.getModeration() == null || telegram.getModeration().getChatId() == null
                || telegram.getModeration().getChatId().isBlank()) {
            throw new IllegalStateException("Invalid configuration: telegram.moderation.chat-id must not be blank when publication.moderation-enabled=true");
        }
        if (telegram.getAdmin() == null || telegram.getAdmin().getUserIds() == null
                || telegram.getAdmin().getUserIds().isEmpty()) {
            throw new IllegalStateException("Invalid configuration: telegram.admin.user-ids must not be empty when publication.moderation-enabled=true");
        }
    }
}
