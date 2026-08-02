package ru.murad.yourmarket.service;
import org.junit.jupiter.api.Test;
import ru.murad.yourmarket.config.TelegramProperties;
import ru.murad.yourmarket.service.impl.TelegramChannelLinkServiceImpl;
import static org.junit.jupiter.api.Assertions.*;
class TelegramChannelLinkServiceTest {
 @Test void usernameWithAt(){assertEquals("https://t.me/market/7",service("@market").messageUrl(7));}
 @Test void usernameWithoutAt(){assertEquals("https://t.me/market/7",service("market").messageUrl(7));}
 @Test void missingUsername(){assertNull(service("").messageUrl(7));}
 @Test void missingMessageId(){assertNull(service("market").messageUrl(null));}
 private TelegramChannelLinkService service(String u){return new TelegramChannelLinkServiceImpl(new TelegramProperties(
  new TelegramProperties.Bot("b","t"),new TelegramProperties.Channel("-1001",u,"")));}
}
