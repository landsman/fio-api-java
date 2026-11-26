# fio-api-java

A client for [Fio banka](https://www.fio.cz/)'s *API Bankovnictví*, for Java 21+ and Kotlin: the movements on an
account, read with the token its holder issues in internet banking, and payment orders sent to it for signing. Its only dependencies are
Jackson 3 and JSpecify. Published on GitHub Packages, not Maven Central.

Written against the official [API_Bankovnictvi.pdf](https://www.fio.cz/docs/cz/API_Bankovnictvi.pdf), version 1.9
of 16 October 2025, and responses captured by other clients.

| Read | For |
|---|---|
| [How Fio's API behaves](.docs/fio-api.md) | what bites: the bookmark, the status codes, the two shapes of a date |
| [Installing](.docs/install.md) | the artifact on GitHub Packages, for Gradle and Maven |
| [Development](.docs/development.md) | build, tests, releasing |

## Use

```java
var fio = new FioClient();                       // one per application; thread-safe, closeable
var token = new FioToken(System.getenv("FIO_TOKEN"));

FioAccountStatement statement = fio.transactionsSinceLastDownload(token);
for (FioTransaction t : statement.transactions()) {
    store(t.id(), t.date(), t.amountInMinorUnits(), t.currency(), t.variableSymbol(), t.rawJson());
}
```

**Reading moves Fio's bookmark**, so store before treating a read as done, and wind back when storing fails:

```java
try {
    store(fio.transactionsSinceLastDownload(token));
} catch (FioUnreadableSinceLastException | StorageFailure e) {
    // Fio may have moved past what it could not give us, or what we could not keep. Winding back is a request
    // too: wait out the 30 seconds after the read (throttle.waitFor(token)), or it is a 409.
    rewindLater(token, lastStoredId);            // then fio.setLastId(token, lastStoredId)
}
```

Other calls: `transactionsForPeriod(token, from, to)` leaves the bookmark alone; `setLastId` and `setLastDate`
move it. Every call takes an optional `Consumer<FioCall>` that hears about the request, for a caller's telemetry,
with the token masked and no movements in it.

Fio allows **one request per token per 30 seconds**. `TokenThrottle` keeps a caller under it, keyed by the token
(or by its text, when one throttle also guards another bank's tokens):

```java
var throttle = new TokenThrottle<FioToken>();
if (throttle.tryAcquire(token)) { fio.transactionsSinceLastDownload(token); }
```

## Payment orders

`importPayments` sends orders within the Czech Republic as one batch. **Nothing is paid when it returns**: Fio holds
the batch in internet banking (*Přehledy, Příkazy k podpisu*) until someone with the right to the account signs it.
It needs the token that can also import orders, not the read-only one.

```java
var refund = new FioDomesticPayment(
        "2400222222", Currency.getInstance("CZK"), 12_345,      // from, currency, 123.45 CZK in haléře
        "2000000699", "0300", LocalDate.now(),                  // to, its bank, due
        "20261126", null, null,                                 // variable, constant, specific symbol
        "Refund of order 20261126", null);                      // for the payee, own note
FioImportResult result = fio.importPayments(token, List.of(refund));
if (!result.accepted()) { show(result.messages(), result.orderMessages()); }
```

Fio refusing the orders is an answer (`accepted()` false), not an exception. **After `FioNoAnswerException` or
`FioInvalidResponseException`, do not send the batch again** before looking in internet banking: Fio may have taken
it before the answer was lost. The same holds for `FioInvalidTokenException` on an import: Fio documents its 500 as a
dead token, but a failure while handling the batch would look the same. Keep `instructionIds()`, which find the orders there. Euro and foreign payments are
not here yet.

## Spring Boot

`cz.insuit:fio-api-spring-boot-starter` brings the client and gives the application a `FioClient` bean and a shared
`TokenThrottle<FioToken>`. Nothing has to be set:

```yaml
fio:
  base-uri: http://localhost:8089/v1/rest   # only for a mock server; production otherwise
  request-timeout: 30s                      # 60 seconds otherwise
  token: ${FIO_TOKEN}                        # only for an app that reads one account; gives a FioToken bean
```

An application's own bean of either type replaces the starter's, a `TokenThrottle<String>` too, for one throttle
that also guards another bank's tokens. Spring matches the type argument, so such an app has no
`TokenThrottle<FioToken>` left and injects its throttle as `TokenThrottle<String>`. Compiled against Spring Boot 3.5,
and tested only there so far.

## Failures

`FioApiException` is sealed, with one subclass per thing to do, so a `switch` over it is exhaustive:

| Exception | When | Do |
|---|---|---|
| `FioInvalidTokenException` | 500: the token does not exist or is inactive | once it lasts for hours, tell the account holder; Fio has answered 500 during outages too |
| `FioRateLimitedException` | 409: the 30 seconds were not kept | try on the next tick |
| `FioTooManyItemsException` | 413: more than 50,000 movements | a shorter period |
| `FioHistoryLockedException` | 422: older than 90 days without the holder's unlock | ask them, or stay within 90 days |
| `FioBadRequestException` | 404 | a bug, ours or the caller's |
| `FioHttpErrorException` | any other status | look at `status()` |
| `FioNoAnswerException` | timeout, refused connection | retry later; on the bookmark read it is `FioUnreadableSinceLastException` instead |
| `FioInvalidResponseException` | an answer this library cannot read | report it; nothing to undo |
| `FioUnreadableSinceLastException` | the bookmark read's answer is unreadable or did not arrive whole | wind the bookmark back, 30 seconds later |

`error()` holds what Fio said in its error document (`errorCode`, `status`, `message`), when it sent one. No
message carries the token. An interrupted thread throws `CancellationException`, its flag kept set.

## Kotlin

JSpecify reaches Kotlin as `String` / `String?`, enforced from Kotlin 2.1; on older versions add
`-Xjspecify-annotations=strict`. `FioApiException` is exhaustive in a `when` without `else`.

## Not here

No euro (*T2Transaction*) or foreign payments, no direct debits, no ABO or pain.001 upload: the domestic order is
the one written so far. No MT940, CAMT or GPC: JSON carries the same. No persistence.

Licence: [Apache 2.0](LICENSE)
