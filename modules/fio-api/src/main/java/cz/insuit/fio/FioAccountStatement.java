package cz.insuit.fio;

import static java.util.Objects.requireNonNull;

import java.util.List;

/**
 * A statement as Fio returns it: the account's header and the movements asked for. {@link #transactions} is empty,
 * never null, when nothing moved: Fio sends both {@code "transactionList": null} and {@code {"transaction": []}} for
 * that, and a caller should not have to tell "no movements" from "no answer".
 */
public record FioAccountStatement(FioAccountInfo info, List<FioTransaction> transactions) {

    public FioAccountStatement {
        requireNonNull(info, "info");
        transactions = List.copyOf(transactions);
    }
}
