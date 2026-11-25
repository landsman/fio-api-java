package cz.insuit.fio;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * The header Fio returns with every statement: whose account the token opens, and where the bookmark stands.
 *
 * @param accountId the account number without the bank code, such as {@code 2400222222}
 * @param bankId the Czech four-digit bank code, {@code 2010} for Fio
 * @param currency ISO 4217
 * @param idFrom the id of the first movement in this answer; null when there are none
 * @param idTo the id of the last movement in this answer; null when there are none
 * @param idLastDownload where Fio's bookmark stood <b>after</b> this request; null when the token has never read
 */
public record FioAccountInfo(
        @Nullable String accountId,
        @Nullable String bankId,
        @Nullable String currency,
        @Nullable String iban,
        @Nullable String bic,
        @Nullable BigDecimal openingBalance,
        @Nullable BigDecimal closingBalance,
        @Nullable Long idFrom,
        @Nullable Long idTo,
        @Nullable Long idLastDownload) {}
