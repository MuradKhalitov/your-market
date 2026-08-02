package ru.murad.yourmarket.service;
import org.junit.jupiter.api.Test;import org.springframework.jdbc.core.JdbcTemplate;
import ru.murad.yourmarket.config.TelegramProperties;import ru.murad.yourmarket.service.impl.RateLimitServiceImpl;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.ArgumentMatchers.*;import static org.mockito.Mockito.*;
class RateLimitServiceTest {
 JdbcTemplate jdbc=mock(JdbcTemplate.class);
 @Test void belowLimitAllowed(){when(jdbc.queryForObject(anyString(),eq(Integer.class),any(),any(),any(),any())).thenReturn(2);assertTrue(service(true,20).allow(1L,"MESSAGE"));}
 @Test void exceededRejected(){when(jdbc.queryForObject(anyString(),eq(Integer.class),any(),any(),any(),any())).thenReturn(21);assertFalse(service(true,20).allow(1L,"MESSAGE"));}
 @Test void disabledDoesNotTouchDatabase(){assertTrue(service(false,1).allow(1L,"MESSAGE"));verifyNoInteractions(jdbc);}
 @Test void cleanupDeletesOnlyConfiguredBatch(){when(jdbc.update(anyString(),any(),eq(500))).thenReturn(17);assertEquals(17,service(true,20).cleanupExpired());}
 private RateLimitService service(boolean enabled,int max){return new RateLimitServiceImpl(jdbc,new TelegramProperties(
  new TelegramProperties.Bot("b","t"),new TelegramProperties.Channel("-1001","c",""),
  new TelegramProperties.Moderation(""),new TelegramProperties.Admin(java.util.List.of()),new TelegramProperties.RateLimit(enabled,max,60)), mock(OperationalMetrics.class));}
}
