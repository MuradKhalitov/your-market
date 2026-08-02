package ru.murad.yourmarket.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.URL;

@Getter @Setter @NoArgsConstructor
@ConfigurationProperties(prefix = "telegram")
@Validated
public class TelegramProperties {
    @Valid @NotNull
    private Bot bot = new Bot();
    @Valid @NotNull
    private Channel channel = new Channel();
    @Valid @NotNull
    private Moderation moderation = new Moderation();
    @Valid @NotNull
    private Admin admin = new Admin();
    @Valid @NotNull
    private RateLimit rateLimit = new RateLimit();

    public TelegramProperties(Bot bot, Channel channel) {
        this.bot = bot; this.channel = channel;
    }
    public TelegramProperties(Bot bot, Channel channel, Moderation moderation, Admin admin, RateLimit rateLimit) {
        this.bot=bot;this.channel=channel;this.moderation=moderation;this.admin=admin;this.rateLimit=rateLimit;
    }
    public Bot bot(){return bot;} public Channel channel(){return channel;}
    public Moderation moderation(){return moderation;} public Admin admin(){return admin;} public RateLimit rateLimit(){return rateLimit;}

    @Getter @Setter @NoArgsConstructor public static class Bot {@NotBlank private String username="";@NotBlank private String token="";public Bot(String username,String token){this.username=username;this.token=token;}public String username(){return username;}public String token(){return token;}}
    @Getter @Setter @NoArgsConstructor public static class Channel {@NotBlank @Pattern(regexp="^-100\\d+$") private String id="";@NotBlank private String username="";@NotBlank @URL private String url="";public Channel(String id,String username,String url){this.id=id;this.username=username;this.url=url;}public String id(){return id;}public String username(){return username;}public String url(){return url;}}
    @Getter @Setter @NoArgsConstructor public static class Moderation {private String chatId="";public Moderation(String chatId){this.chatId=chatId;}public String chatId(){return chatId;}}
    @Getter @Setter @NoArgsConstructor public static class Admin {private List<Long> userIds=new ArrayList<>();public Admin(List<Long> userIds){this.userIds=userIds;}public List<Long> userIds(){return userIds;}}
    @Getter @Setter @NoArgsConstructor public static class RateLimit {
        private boolean enabled=true;private int maxActions=20;private int windowSeconds=60;private int retentionHours=24;private int cleanupBatchSize=500;private int cleanupMaxBatches=10;
        public RateLimit(boolean enabled,int maxActions,int windowSeconds){this.enabled=enabled;this.maxActions=maxActions;this.windowSeconds=windowSeconds;}
        public RateLimit(boolean enabled,int maxActions,int windowSeconds,int retentionHours,int cleanupBatchSize,int cleanupMaxBatches){this(enabled,maxActions,windowSeconds);this.retentionHours=retentionHours;this.cleanupBatchSize=cleanupBatchSize;this.cleanupMaxBatches=cleanupMaxBatches;}
        public boolean enabled(){return enabled;}public int maxActions(){return maxActions;}public int windowSeconds(){return windowSeconds;}public int retentionHours(){return retentionHours;}public int cleanupBatchSize(){return cleanupBatchSize;}public int cleanupMaxBatches(){return cleanupMaxBatches;}
    }
}
