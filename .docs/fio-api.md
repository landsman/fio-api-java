# How Fio's API behaves

What the documentation does not make obvious, each pinned by a test.

## The base URL moved

`https://www.fio.cz/ib_api/` was switched off on **30 November 2024** for `https://fioapi.fio.cz/`. An example, blog
post or library from before that points at nothing.

## The status codes do not mean what they usually mean

| Code | What it means at Fio |
|---|---|
| **409** | the 30-second minimum interval for this token was not kept; reads and writes share it |
| **500** | the token does not exist or is inactive. Usually not a server fault: a token lasts 180 days at most, so this is every token's expected end. But Fio has been seen answering 500 while its API was down too |
| **413** | more than 50,000 movements matched |
| **422** | older than 90 days, without the unlock the holder grants in internet banking (ten minutes) |
| **404** | a malformed request |

Reading 500 as "the bank is having a moment" turns the expected end of a token into a poller that hammers a dead
credential and never tells anybody. Reading one 500 as final turns a night's outage into a dead connection and a
"your token was refused" message for a token that still works. So wait for the refusal to last, a few hours of polls
without a success, before telling the holder.

The body of a failure is often Fio's error document:

```xml
<response><result><errorCode>21</errorCode><status>error</status><message>Výpis neexistuje</message></result></response>
```

Fio publishes no list of codes, so the library reads the document into `FioApiException.error()` and still decides by
status. Which code a dead token gets is not known yet; once seen, it can tell a dead token from a real outage.

## Reading moves the bookmark

`transactionsSinceLastDownload` advances Fio's server-side bookmark (*zarážka*) whenever the answer holds movements,
before the caller has stored anything. A read that fails afterwards, storing or parsing, loses those movements
from that endpoint: still on the account, reachable only through `setLastId` or a period read. So the library
throws `FioUnreadableSinceLastException` rather than `FioInvalidResponseException` or `FioNoAnswerException` when the bookmark read's answer cannot be
read or did not arrive whole (a timeout may come after Fio served it), and the caller winds back. Winding back is a
request too, so it waits out the 30 seconds after the failed read, or it is a 409. An answer without movements leaves
the bookmark alone.

A `transaction` that is there but not a list is refused rather than read as empty, and so is a number that is there
but unreadable: either would quietly lose movements.

## A field has two shapes, and both are real

- **A date** is epoch milliseconds (`1340661600000`, the documentation's JSON examples, midnight in Prague) or a
  date with an offset (`2015-03-30+0200`, its field table and a response captured in the wild; `2012-07-27+02:00`
  in its XML). The offset says where midnight is: `2015-03-30+0200` is 22:00 UTC on the 29th, and the booking day is
  still the 30th. All three forms are read, and `Z` or no offset too.
- **An empty statement** is `"transactionList": null` (the documentation) or `{"transaction": []}` (the wild). Both
  are an empty list.

## Money

Jackson needs telling twice: `USE_BIG_DECIMAL_FOR_FLOATS`, or `-15.00` is a `double`, and
`STRIP_TRAILING_BIGDECIMAL_ZEROES` off, or it arrives as `-15`. Amounts keep the scale Fio sent.

## Ids

*ID pohybu* (`id`) is unique on the account and never reused: the key to store a movement once. *ID pokynu*
(`instructionId`) is not: a foreign payment books the transfer and its fee under one instruction.

## Prior art

| Library | Note |
|---|---|
| [martiner/fio-java](https://github.com/martiner/fio-java) | maintained (0.6.0, August 2026). Spring `RestTemplate`, JAXB over XML, one token per instance. Reads the error document; leaves the 30 seconds, the meaning of 500/413/422 and the bookmark to the caller |
| [contributte/fio](https://github.com/contributte/fio) | PHP, maintained; no rate limiting |
| [mhujer/fio-api-php](https://github.com/mhujer/fio-api-php) | PHP, last pushed June 2024, before the URL moved. Its captured responses showed the string date form; two are copied into the tests with the names and accounts replaced, see [the fixtures' README](../modules/fio-api/src/test/resources/fio/README.md) |
