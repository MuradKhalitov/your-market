package ru.murad.yourmarket.exception;
public class TelegramConfirmedFailureException extends TelegramPublicationException {
    private final Integer errorCode;
    private final String apiDescription;

    public TelegramConfirmedFailureException(String message, Integer errorCode, String apiDescription, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.apiDescription = apiDescription;
    }

    public Integer getErrorCode() { return errorCode; }
    public String getApiDescription() { return apiDescription; }
    public boolean isCurrencyTotalAmountInvalid() {
        return apiDescription != null && apiDescription.contains("CURRENCY_TOTAL_AMOUNT_INVALID");
    }
}
