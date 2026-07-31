package ru.murad.yourmarket.service;
import ru.murad.yourmarket.model.AdvertisementChannelMessage;
import java.util.*;
public interface ChannelMessageDeletionTransactionService {
 List<UUID> candidates(UUID advertisementId);
 AdvertisementChannelMessage claim(UUID messageId);
 void complete(UUID messageId,UUID operationId);
 void fail(UUID messageId,UUID operationId,String reason);
 boolean allDeleted(UUID advertisementId);
}
