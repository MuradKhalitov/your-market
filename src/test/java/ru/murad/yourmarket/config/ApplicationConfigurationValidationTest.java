package ru.murad.yourmarket.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ApplicationConfigurationValidationTest {
    private static final String[] VALID = {
            "telegram.bot.username=test_bot", "telegram.bot.token=123456:test",
            "telegram.channel.id=-1001234567890", "telegram.channel.username=test_channel",
            "telegram.channel.url=https://t.me/test_channel", "telegram.payment.provider-token=test-provider",
            "publication.price=199.00", "publication.currency=RUB", "publication.lifetime-days=30",
            "publication.expiration-cron=0 0 * * * *", "publication.deletion-claim-timeout-seconds=180",
            "publication.expiration-claim-timeout-seconds=600"
    };
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class).withPropertyValues(VALID);

    @Test void blankBotTokenFails(){fails("telegram.bot.token=", "telegram.bot.token");}
    @Test void blankChannelIdFails(){fails("telegram.channel.id=", "telegram.channel.id");}
    @Test void blankProviderTokenFails(){fails("telegram.payment.provider-token=", "telegram.payment.providerToken");}
    @Test void nonPositivePriceFails(){fails("publication.price=0", "publication.price");}
    @Test void moderationWithoutChatFails(){runner.withPropertyValues("publication.moderation-enabled=true","telegram.admin.user-ids=1").run(c->{assertThat(c).hasFailed();assertThat(root(c.getStartupFailure())).hasMessageContaining("telegram.moderation.chat-id");});}
    @Test void moderationWithoutAdminsFails(){runner.withPropertyValues("publication.moderation-enabled=true","telegram.moderation.chat-id=-1001234567890").run(c->{assertThat(c).hasFailed();assertThat(root(c.getStartupFailure())).hasMessageContaining("telegram.admin.user-ids");});}
    @Test void validConfigurationStarts(){runner.run(c->assertThat(c).hasNotFailed());}

    private void fails(String property,String expected){runner.withPropertyValues(property).run(c->{assertThat(c).hasFailed();assertThat(root(c.getStartupFailure()).getMessage()).containsIgnoringCase(expected);});}
    private Throwable root(Throwable value){Throwable result=value;while(result.getCause()!=null)result=result.getCause();return result;}

    @Configuration(proxyBeanMethods=false)
    @EnableConfigurationProperties({TelegramProperties.class,PublicationProperties.class})
    static class TestConfig {@Bean ApplicationConfigurationValidator validator(PublicationProperties p,TelegramProperties t){return new ApplicationConfigurationValidator(p,t);}}
}
