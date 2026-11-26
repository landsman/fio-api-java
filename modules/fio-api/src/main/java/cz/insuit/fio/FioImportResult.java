package cz.insuit.fio;

import static java.util.Objects.requireNonNull;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Fio's answer to an import, its {@code responseImport}. An accepted batch is <b>not yet a payment</b>: it waits in
 * internet banking until a person with the right to the account signs it there (SMS or Fio podpis).
 *
 * @param errorCode {@code 0} accepted, {@code 1} the orders failed Fio's checks, {@code 2} accepted with warnings,
 *     {@code 11} a syntax error, {@code 12} and {@code 14} no orders, {@code 13} a file over 2 MB
 * @param status {@code ok}, {@code warning} (accepted, but a value disagrees, such as the currency), {@code error}
 *     (the whole batch refused) or {@code fatal} (Fio's own failure, the whole batch refused), as Fio sent it
 * @param instructionId Fio's id of the batch, to find it in internet banking; null when none was created
 * @param messages what Fio said about the batch as a whole
 * @param orderMessages what Fio said about single orders
 */
public record FioImportResult(
        int errorCode,
        String status,
        @Nullable Long instructionId,
        List<String> messages,
        List<OrderMessage> orderMessages) {

    public FioImportResult {
        requireNonNull(status, "status");
        messages = List.copyOf(messages);
        orderMessages = List.copyOf(orderMessages);
    }

    /** Whether Fio took the batch, warnings or not; it still waits for a signature. */
    public boolean accepted() {
        return status.equals("ok") || status.equals("warning");
    }

    /**
     * One message about one order.
     *
     * @param orderId the order's id in the batch, as Fio numbers it; null when Fio sent none
     * @param status {@code ok}, {@code warning}, {@code error} or {@code fatal}; null when Fio sent none
     * @param errorCode Fio's code for the message; null when Fio sent none
     */
    public record OrderMessage(
            @Nullable Long orderId,
            @Nullable String status,
            @Nullable Integer errorCode,
            String text) {

        public OrderMessage {
            requireNonNull(text, "text");
        }
    }
}
