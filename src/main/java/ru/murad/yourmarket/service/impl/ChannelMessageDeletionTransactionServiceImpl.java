package ru.murad.yourmarket.service.impl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.murad.yourmarket.model.AdvertisementChannelMessage;
import ru.murad.yourmarket.model.enums.ChannelMessageStatus;
import ru.murad.yourmarket.repository.AdvertisementChannelMessageRepository;
import ru.murad.yourmarket.service.ChannelMessageDeletionTransactionService;
import java.time.Instant;
import java.util.*;
@Service @RequiredArgsConstructor
public class ChannelMessageDeletionTransactionServiceImpl implements ChannelMessageDeletionTransactionService {
 private final AdvertisementChannelMessageRepository repository;
 private final ru.murad.yourmarket.config.PublicationProperties properties;
 @Override @Transactional(readOnly=true) public List<UUID> candidates(UUID ad){return repository.findByAdvertisementIdOrderByPosition(ad).stream().filter(m->m.getDeletionStatus()!=ChannelMessageStatus.DELETED).map(AdvertisementChannelMessage::getId).toList();}
 @Override @Transactional public AdvertisementChannelMessage claim(UUID id){var m=repository.findByIdForUpdate(id).orElse(null);if(m==null||m.getDeletionStatus()==ChannelMessageStatus.DELETED||m.getDeletionStatus()==ChannelMessageStatus.DELETE_RECONCILIATION_REQUIRED)return null;if(m.getDeletionStatus()==ChannelMessageStatus.DELETE_IN_PROGRESS){if(m.getDeletionStartedAt()!=null&&m.getDeletionStartedAt().isBefore(Instant.now().minusSeconds(properties.getDeletionClaimTimeoutSeconds())))m.setDeletionStatus(ChannelMessageStatus.DELETE_RECONCILIATION_REQUIRED);return null;}m.setDeletionStatus(ChannelMessageStatus.DELETE_IN_PROGRESS);m.setDeletionStartedAt(Instant.now());m.setDeleteAttempts(m.getDeleteAttempts()+1);m.setDeleteOperationId(UUID.randomUUID());return repository.save(m);}
 @Override @Transactional public void complete(UUID id,UUID op){var m=repository.findByIdForUpdate(id).orElseThrow();if(m.getDeletionStatus()==ChannelMessageStatus.DELETE_IN_PROGRESS&&Objects.equals(op,m.getDeleteOperationId())){m.setDeletionStatus(ChannelMessageStatus.DELETED);m.setDeletedAt(Instant.now());m.setDeletionStartedAt(null);m.setDeleteOperationId(null);m.setLastDeleteError(null);}}
 @Override @Transactional public void fail(UUID id,UUID op,String reason){var m=repository.findByIdForUpdate(id).orElseThrow();if(m.getDeletionStatus()==ChannelMessageStatus.DELETE_IN_PROGRESS&&Objects.equals(op,m.getDeleteOperationId())){m.setDeletionStatus(ChannelMessageStatus.ACTIVE);m.setDeletionStartedAt(null);m.setDeleteOperationId(null);m.setLastDeleteError(safe(reason));}}
 @Override @Transactional(readOnly=true) public boolean allDeleted(UUID ad){return repository.countByAdvertisementIdAndDeletionStatusNot(ad,ChannelMessageStatus.DELETED)==0;}
 private String safe(String s){return s==null?"Telegram error":s.substring(0,Math.min(500,s.length()));}
}
