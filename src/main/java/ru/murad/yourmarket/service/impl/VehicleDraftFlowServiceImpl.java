package ru.murad.yourmarket.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Year;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.murad.yourmarket.exception.DraftValidationException;
import ru.murad.yourmarket.exception.InvalidAdvertisementStateException;
import ru.murad.yourmarket.model.AdvertisementDraft;
import ru.murad.yourmarket.model.VehicleDraftDetails;
import ru.murad.yourmarket.model.enums.AdvertisementCreationStep;
import ru.murad.yourmarket.model.enums.DriveType;
import ru.murad.yourmarket.model.enums.EngineType;
import ru.murad.yourmarket.model.enums.TransmissionType;
import ru.murad.yourmarket.repository.AdvertisementDraftRepository;
import ru.murad.yourmarket.repository.VehicleDraftDetailsRepository;
import ru.murad.yourmarket.service.VehicleCatalog;
import ru.murad.yourmarket.service.VehicleDraftFlowService;

@Service
@RequiredArgsConstructor
@Transactional
public class VehicleDraftFlowServiceImpl implements VehicleDraftFlowService {
    private final AdvertisementDraftRepository drafts;
    private final VehicleDraftDetailsRepository details;
    private final VehicleCatalog catalog;
    private final Clock clock;

    @Override
    public AdvertisementDraft chooseBrand(Long userId, String code) {
        AdvertisementDraft current = require(userId);
        if (current.getStep() == AdvertisementCreationStep.WAITING_FOR_VEHICLE_MODEL
                && details.findByAdvertisementDraftId(current.getId()).map(VehicleDraftDetails::getBrandCode)
                .filter(code::equals).isPresent()) return current;
        if ("OTHER".equals(code)) {
            return move(userId, AdvertisementCreationStep.WAITING_FOR_VEHICLE_BRAND,
                    AdvertisementCreationStep.WAITING_FOR_CUSTOM_VEHICLE_BRAND, value -> {
                        value.setBrandCode("OTHER");
                        value.setBrandNameSnapshot(null);
                        value.setCustomBrand(null);
                        clearModel(value);
                    });
        }
        VehicleCatalog.Brand brand = catalog.brand(code)
                .orElseThrow(() -> new DraftValidationException("Неизвестная марка автомобиля."));
        return move(userId, AdvertisementCreationStep.WAITING_FOR_VEHICLE_BRAND,
                AdvertisementCreationStep.WAITING_FOR_VEHICLE_MODEL, value -> {
                    value.setBrandCode(brand.code());
                    value.setBrandNameSnapshot(brand.name());
                    value.setCustomBrand(null);
                    clearModel(value);
                });
    }

    @Override
    public AdvertisementDraft setCustomBrand(Long userId, String input) {
        String name = text(input, 2, 40, "Марка должна содержать от 2 до 40 символов.");
        return move(userId, AdvertisementCreationStep.WAITING_FOR_CUSTOM_VEHICLE_BRAND,
                AdvertisementCreationStep.WAITING_FOR_CUSTOM_VEHICLE_MODEL, value -> {
                    value.setBrandCode("OTHER");
                    value.setBrandNameSnapshot(name);
                    value.setCustomBrand(name);
                    clearModel(value);
                });
    }

    @Override
    public AdvertisementDraft chooseModel(Long userId, String code) {
        AdvertisementDraft current = require(userId);
        if (current.getStep() == AdvertisementCreationStep.WAITING_FOR_VEHICLE_YEAR
                && details.findByAdvertisementDraftId(current.getId()).map(VehicleDraftDetails::getModelCode)
                .filter(code::equals).isPresent()) return current;
        if ("OTHER".equals(code)) {
            return move(userId, AdvertisementCreationStep.WAITING_FOR_VEHICLE_MODEL,
                    AdvertisementCreationStep.WAITING_FOR_CUSTOM_VEHICLE_MODEL, value -> {
                        value.setModelCode("OTHER");
                        value.setModelNameSnapshot(null);
                        value.setCustomModel(null);
                    });
        }
        return move(userId, AdvertisementCreationStep.WAITING_FOR_VEHICLE_MODEL,
                AdvertisementCreationStep.WAITING_FOR_VEHICLE_YEAR, value -> {
                    VehicleCatalog.Model model = catalog.models(value.getBrandCode()).stream()
                            .filter(candidate -> candidate.code().equals(code)).findFirst()
                            .orElseThrow(() -> new DraftValidationException("Неизвестная модель автомобиля."));
                    value.setModelCode(model.code());
                    value.setModelNameSnapshot(model.name());
                    value.setCustomModel(null);
                });
    }

    @Override
    public AdvertisementDraft setCustomModel(Long userId, String input) {
        String name = text(input, 1, 60, "Модель должна содержать от 1 до 60 символов.");
        return move(userId, AdvertisementCreationStep.WAITING_FOR_CUSTOM_VEHICLE_MODEL,
                AdvertisementCreationStep.WAITING_FOR_VEHICLE_YEAR, value -> {
                    value.setModelCode("OTHER");
                    value.setModelNameSnapshot(name);
                    value.setCustomModel(name);
                });
    }

    @Override public AdvertisementDraft setYear(Long userId, String input) {
        int year;
        try { year = Integer.parseInt(input.trim()); }
        catch (RuntimeException ex) { throw new DraftValidationException("Введите год целым числом."); }
        int maxYear = Year.now(clock).getValue() + 1;
        if (year < 1900 || year > maxYear) throw new DraftValidationException("Введите год числом от 1900 до " + maxYear + ".");
        return move(userId, AdvertisementCreationStep.WAITING_FOR_VEHICLE_YEAR,
                AdvertisementCreationStep.WAITING_FOR_VEHICLE_TRANSMISSION, value -> value.setProductionYear(year));
    }
    @Override public AdvertisementDraft setTransmission(Long userId, TransmissionType value) {
        if (alreadyAt(userId, AdvertisementCreationStep.WAITING_FOR_VEHICLE_ENGINE_TYPE,
                details -> details.getTransmission() == value)) return require(userId);
        return move(userId, AdvertisementCreationStep.WAITING_FOR_VEHICLE_TRANSMISSION,
                AdvertisementCreationStep.WAITING_FOR_VEHICLE_ENGINE_TYPE, details -> details.setTransmission(value));
    }
    @Override public AdvertisementDraft setEngineType(Long userId, EngineType value) {
        AdvertisementCreationStep next = value == EngineType.ELECTRIC
                ? AdvertisementCreationStep.WAITING_FOR_VEHICLE_MILEAGE : AdvertisementCreationStep.WAITING_FOR_VEHICLE_ENGINE_VOLUME;
        if (alreadyAt(userId, next, details -> details.getEngineType() == value)) return require(userId);
        return move(userId, AdvertisementCreationStep.WAITING_FOR_VEHICLE_ENGINE_TYPE,
                next,
                details -> { details.setEngineType(value); if (value == EngineType.ELECTRIC) details.setEngineVolumeLiters(null); });
    }
    @Override public AdvertisementDraft setEngineVolume(Long userId, String input) {
        BigDecimal value;
        try { value = new BigDecimal(input.trim().replace(',', '.')).setScale(1, RoundingMode.UNNECESSARY); }
        catch (RuntimeException ex) { throw new DraftValidationException("Введите объём с одним знаком после запятой, например 2.0."); }
        if (value.signum() <= 0 || value.compareTo(new BigDecimal("10.0")) > 0)
            throw new DraftValidationException("Объём должен быть больше 0 и не больше 10.0 л.");
        return move(userId, AdvertisementCreationStep.WAITING_FOR_VEHICLE_ENGINE_VOLUME,
                AdvertisementCreationStep.WAITING_FOR_VEHICLE_MILEAGE, details -> details.setEngineVolumeLiters(value));
    }
    @Override public AdvertisementDraft setMileage(Long userId, String input) {
        int value;
        try { value = Integer.parseInt(input.replace(" ", "")); }
        catch (RuntimeException ex) { throw new DraftValidationException("Введите пробег целым числом."); }
        if (value < 0 || value > 3_000_000) throw new DraftValidationException("Пробег должен быть от 0 до 3 000 000 км.");
        return move(userId, AdvertisementCreationStep.WAITING_FOR_VEHICLE_MILEAGE,
                AdvertisementCreationStep.WAITING_FOR_VEHICLE_DRIVE_TYPE, details -> details.setMileageKm(value));
    }
    @Override public AdvertisementDraft setDriveType(Long userId, DriveType value) {
        if (alreadyAt(userId, AdvertisementCreationStep.WAITING_FOR_TITLE,
                details -> details.getDriveType() == value)) return require(userId);
        return move(userId, AdvertisementCreationStep.WAITING_FOR_VEHICLE_DRIVE_TYPE,
                AdvertisementCreationStep.WAITING_FOR_TITLE, details -> details.setDriveType(value));
    }
    @Override public AdvertisementDraft beginBrandSearch(Long userId) { return idempotentStep(userId, AdvertisementCreationStep.WAITING_FOR_VEHICLE_BRAND, AdvertisementCreationStep.WAITING_FOR_VEHICLE_BRAND_SEARCH); }
    @Override public AdvertisementDraft beginModelSearch(Long userId) { return idempotentStep(userId, AdvertisementCreationStep.WAITING_FOR_VEHICLE_MODEL, AdvertisementCreationStep.WAITING_FOR_VEHICLE_MODEL_SEARCH); }
    @Override public AdvertisementDraft backToBrands(Long userId) {
        AdvertisementDraft draft = require(userId);
        if (draft.getStep() == AdvertisementCreationStep.WAITING_FOR_VEHICLE_BRAND) return draft;
        if (draft.getStep() != AdvertisementCreationStep.WAITING_FOR_VEHICLE_MODEL
                && draft.getStep() != AdvertisementCreationStep.WAITING_FOR_VEHICLE_MODEL_SEARCH)
            throw new InvalidAdvertisementStateException("Этот шаг уже завершён.");
        details.findByAdvertisementDraftId(draft.getId()).ifPresent(value -> { clearModel(value); details.save(value); });
        draft.setStep(AdvertisementCreationStep.WAITING_FOR_VEHICLE_BRAND);
        return drafts.save(draft);
    }
    @Override public AdvertisementDraft setBrandSearchResult(Long userId, String code) {
        AdvertisementDraft draft = require(userId);
        if (draft.getStep() != AdvertisementCreationStep.WAITING_FOR_VEHICLE_BRAND_SEARCH) throw new InvalidAdvertisementStateException("Этот шаг уже завершён.");
        draft.setStep(AdvertisementCreationStep.WAITING_FOR_VEHICLE_BRAND);
        drafts.save(draft);
        return chooseBrand(userId, code);
    }
    @Override @Transactional(readOnly = true) public Optional<VehicleDraftDetails> find(Long userId) { return drafts.findByTelegramUserId(userId).flatMap(draft -> details.findByAdvertisementDraftId(draft.getId())); }
    @Override public void deleteForDraft(UUID draftId) { details.deleteByAdvertisementDraftId(draftId); }

    private AdvertisementDraft move(Long userId, AdvertisementCreationStep expected, AdvertisementCreationStep next,
                                    java.util.function.Consumer<VehicleDraftDetails> change) {
        AdvertisementDraft draft = require(userId);
        if (draft.getStep() != expected) throw new InvalidAdvertisementStateException("Этот шаг уже завершён.");
        VehicleDraftDetails value = details.findByAdvertisementDraftId(draft.getId())
                .orElseGet(() -> VehicleDraftDetails.builder().advertisementDraftId(draft.getId()).build());
        change.accept(value);
        details.save(value);
        draft.setStep(next);
        return drafts.save(draft);
    }
    private AdvertisementDraft step(Long userId, AdvertisementCreationStep expected, AdvertisementCreationStep next) {
        AdvertisementDraft draft = require(userId);
        if (draft.getStep() != expected) throw new InvalidAdvertisementStateException("Этот шаг уже завершён.");
        draft.setStep(next);
        return drafts.save(draft);
    }
    private AdvertisementDraft idempotentStep(Long userId, AdvertisementCreationStep expected, AdvertisementCreationStep next) {
        AdvertisementDraft draft = require(userId);
        if (draft.getStep() == next) return draft;
        if (draft.getStep() != expected) throw new InvalidAdvertisementStateException("Этот шаг уже завершён.");
        draft.setStep(next);
        return drafts.save(draft);
    }
    private boolean alreadyAt(Long userId, AdvertisementCreationStep step,
                              java.util.function.Predicate<VehicleDraftDetails> predicate) {
        AdvertisementDraft draft = require(userId);
        return draft.getStep() == step && details.findByAdvertisementDraftId(draft.getId()).filter(predicate).isPresent();
    }
    private AdvertisementDraft require(Long userId) { return drafts.findByTelegramUserId(userId).orElseThrow(() -> new InvalidAdvertisementStateException("Черновик не найден.")); }
    private String text(String input, int min, int max, String message) {
        String result = input == null ? "" : input.trim();
        if (result.length() < min || result.length() > max || result.chars().anyMatch(Character::isISOControl)) throw new DraftValidationException(message);
        return result;
    }
    private void clearModel(VehicleDraftDetails value) { value.setModelCode(null); value.setModelNameSnapshot(null); value.setCustomModel(null); }
}
