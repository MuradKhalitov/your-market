package ru.murad.yourmarket.service.impl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.murad.yourmarket.service.*;
import ru.murad.yourmarket.telegram.TelegramGateway;
import java.util.UUID;
@Slf4j @Service @RequiredArgsConstructor
public class ChannelMessageDeletionServiceImpl implements ChannelMessageDeletionService {
 private final ChannelMessageDeletionTransactionService transactions; private final TelegramGateway telegram;
 @Override public boolean deleteAll(UUID ad){for(UUID id:transactions.candidates(ad)){var message=transactions.claim(id);if(message==null)continue;try{telegram.deleteChannelMessage(message.getChannelMessageId());transactions.complete(id,message.getDeleteOperationId());}catch(ru.murad.yourmarket.exception.TelegramMessageAlreadyAbsentException ex){transactions.complete(id,message.getDeleteOperationId());}catch(RuntimeException ex){transactions.fail(id,message.getDeleteOperationId(),ex.getMessage());log.warn("Не удалено сообщение advertisementId={}, messageId={}",ad,message.getChannelMessageId());}}return transactions.allDeleted(ad);}
}
