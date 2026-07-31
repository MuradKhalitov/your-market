package ru.murad.yourmarket.service.impl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.murad.yourmarket.config.TelegramProperties;
import ru.murad.yourmarket.service.RateLimitService;
import java.sql.Timestamp;
import java.time.Instant;

@Slf4j @Service @RequiredArgsConstructor
public class RateLimitServiceImpl implements RateLimitService {
    private final JdbcTemplate jdbc; private final TelegramProperties properties;
    public boolean allow(Long userId,String action) {
        var cfg=properties.rateLimit(); if(!cfg.enabled()) return true;
        Instant boundary=Instant.now().minusSeconds(cfg.windowSeconds());
        Integer count=jdbc.queryForObject("""
            INSERT INTO telegram_rate_limits(telegram_user_id,action_type,window_started_at,action_count)
            VALUES (?,?,now(),1)
            ON CONFLICT (telegram_user_id,action_type) DO UPDATE SET
              action_count=CASE WHEN telegram_rate_limits.window_started_at < ? THEN 1 ELSE telegram_rate_limits.action_count+1 END,
              window_started_at=CASE WHEN telegram_rate_limits.window_started_at < ? THEN now() ELSE telegram_rate_limits.window_started_at END
            RETURNING action_count
            """,Integer.class,userId,action,Timestamp.from(boundary),Timestamp.from(boundary));
        boolean allowed=count!=null&&count<=cfg.maxActions();
        if(!allowed) log.warn("Rate limit rejected telegramUserId={}, actionType={}",userId,action);
        return allowed;
    }
    @Override public int cleanupExpired() {
        Instant cutoff=Instant.now().minusSeconds(properties.rateLimit().retentionHours()*3600L);
        return jdbc.update("WITH candidates AS (SELECT ctid FROM telegram_rate_limits WHERE window_started_at < ? ORDER BY window_started_at FOR UPDATE SKIP LOCKED LIMIT ?) DELETE FROM telegram_rate_limits t USING candidates c WHERE t.ctid=c.ctid",
                Timestamp.from(cutoff),properties.rateLimit().cleanupBatchSize());
    }
}
