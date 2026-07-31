package ru.murad.yourmarket.service;

import org.junit.jupiter.api.*;
import org.mockito.*;
import ru.murad.yourmarket.exception.DraftValidationException;
import ru.murad.yourmarket.model.AdvertisementDraft;
import ru.murad.yourmarket.model.enums.*;
import ru.murad.yourmarket.repository.AdvertisementDraftRepository;
import ru.murad.yourmarket.service.impl.AdvertisementDraftServiceImpl;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvertisementDraftServiceTest {
    AdvertisementDraftRepository repository = mock(AdvertisementDraftRepository.class);
    ru.murad.yourmarket.repository.AdvertisementDraftPhotoRepository photos = mock(ru.murad.yourmarket.repository.AdvertisementDraftPhotoRepository.class);
    ru.murad.yourmarket.repository.AdvertisementRepository ads = mock(ru.murad.yourmarket.repository.AdvertisementRepository.class);
    ru.murad.yourmarket.repository.PaymentRepository payments = mock(ru.murad.yourmarket.repository.PaymentRepository.class);
    AdvertisementDraftServiceImpl service = new AdvertisementDraftServiceImpl(repository, photos, ads, payments);

    @Test
    void startsCreation() {
        when(repository.findByTelegramUserId(1L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        AdvertisementDraft draft = service.startCreation(1L, 10L);
        assertEquals(AdvertisementCreationStep.WAITING_FOR_CATEGORY, draft.getStep());
        assertEquals(10L, draft.getChatId());
    }

    @Test
    void movesBetweenSteps() {
        AdvertisementDraft draft = draft(AdvertisementCreationStep.WAITING_FOR_CATEGORY);
        when(repository.findByTelegramUserId(1L)).thenReturn(Optional.of(draft));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.setCategory(1L, AdvertisementCategory.AUTO);
        assertEquals(AdvertisementCreationStep.WAITING_FOR_TITLE, draft.getStep());
        assertEquals(AdvertisementCategory.AUTO, draft.getCategory());
    }

    @Test
    void invalidPriceDoesNotChangeStep() {
        AdvertisementDraft draft = draft(AdvertisementCreationStep.WAITING_FOR_PRICE);
        when(repository.findByTelegramUserId(1L)).thenReturn(Optional.of(draft));
        assertThrows(DraftValidationException.class, () -> service.setPrice(1L, "-10"));
        assertEquals(AdvertisementCreationStep.WAITING_FOR_PRICE, draft.getStep());
        verify(repository, never()).save(any());
    }

    @Test
    void cancelDeletesDraft() {
        service.cancel(1L);
        verify(repository).deleteByTelegramUserId(1L);
    }

    @Test
    void movesBackAndKeepsExistingValue() {
        AdvertisementDraft draft = draft(AdvertisementCreationStep.WAITING_FOR_DESCRIPTION);
        draft.setTitle("Старое название");
        when(repository.findByTelegramUserId(1L)).thenReturn(Optional.of(draft));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        AdvertisementDraft result = service.moveToPreviousStep(1L);
        assertEquals(AdvertisementCreationStep.WAITING_FOR_TITLE, result.getStep());
        assertEquals("Старое название", result.getTitle());
    }

    @Test
    void firstStepDoesNotMoveBack() {
        AdvertisementDraft draft = draft(AdvertisementCreationStep.WAITING_FOR_CATEGORY);
        when(repository.findByTelegramUserId(1L)).thenReturn(Optional.of(draft));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals(AdvertisementCreationStep.WAITING_FOR_CATEGORY, service.moveToPreviousStep(1L).getStep());
    }

    @Test
    void requiresAtLeastOnePhotoAndRejectsSixth() {
        AdvertisementDraft draft = draft(AdvertisementCreationStep.WAITING_FOR_PHOTO); draft.setId(java.util.UUID.randomUUID());
        when(repository.findByTelegramUserId(1L)).thenReturn(Optional.of(draft));
        when(photos.countByDraftId(draft.getId())).thenReturn(0L, 5L);
        assertThrows(DraftValidationException.class, () -> service.finishPhotos(1L));
        assertThrows(DraftValidationException.class, () -> service.addPhoto(1L, "sixth"));
    }

    @Test
    void photoOrderAndClearArePreserved() {
        AdvertisementDraft draft = draft(AdvertisementCreationStep.WAITING_FOR_PHOTO); draft.setId(java.util.UUID.randomUUID());
        when(repository.findByTelegramUserId(1L)).thenReturn(Optional.of(draft));
        when(photos.countByDraftId(draft.getId())).thenReturn(0L);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.addPhoto(1L, "photo");
        var captor=org.mockito.ArgumentCaptor.forClass(ru.murad.yourmarket.model.AdvertisementDraftPhoto.class);
        verify(photos).save(captor.capture()); assertEquals(0,captor.getValue().getPosition());
        service.clearPhotos(1L); verify(photos).deleteByDraftId(draft.getId()); assertNull(draft.getTelegramFileId());
    }

    @Test
    void editingTitleReturnsToPreviewAndReplacesValue() {
        AdvertisementDraft draft=draft(AdvertisementCreationStep.PREVIEW);draft.setId(java.util.UUID.randomUUID());
        when(repository.findByTelegramUserId(1L)).thenReturn(Optional.of(draft));when(repository.save(any())).thenAnswer(i->i.getArgument(0));
        service.beginEdit(1L,AdvertisementCreationStep.WAITING_FOR_TITLE);
        AdvertisementDraft edited=service.setTitle(1L,"Новое название");
        assertEquals("Новое название",edited.getTitle());assertEquals(AdvertisementCreationStep.PREVIEW,edited.getStep());
    }

    private AdvertisementDraft draft(AdvertisementCreationStep step) {
        return AdvertisementDraft.builder().telegramUserId(1L).chatId(10L).step(step).build();
    }
}
