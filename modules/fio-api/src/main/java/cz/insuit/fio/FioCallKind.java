package cz.insuit.fio;

/** Which of Fio's endpoints a request went to. Named rather than logged as a path, because the path holds the token. */
public enum FioCallKind {
    /** {@code last/…/transactions.json}, which moves the bank's bookmark. */
    TRANSACTIONS_SINCE_LAST,

    /** {@code periods/…/transactions.json}, a date range, leaving the bookmark alone. */
    TRANSACTIONS_FOR_PERIOD,

    /** {@code set-last-id/…}, winding the bookmark to a movement. */
    SET_LAST_ID,

    /** {@code set-last-date/…}, winding the bookmark to a day. */
    SET_LAST_DATE,
}
