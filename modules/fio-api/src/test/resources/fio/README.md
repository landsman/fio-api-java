# Fixtures

| File | From |
|---|---|
| `period.json`, `empty.json`, `with-symbols.json` | the JSON examples in Fio's [API_Bankovnictvi.pdf](https://www.fio.cz/docs/cz/API_Bankovnictvi.pdf) v1.9; the names in them are the documentation's |
| `real-world-string-dates.json`, `real-world-empty-array.json` | responses captured by [mhujer/fio-api-php](https://github.com/mhujer/fio-api-php)'s tests, MIT licence, Copyright (c) 2015 Martin Hujer. The shape is kept as captured; names and account numbers are replaced by the documentation's placeholders |

The real-world ones are why a string date and `{"transaction": []}` are read: the documentation's examples show
neither.

## Import

| File | From |
|---|---|
| `schema/importIB.xsd`, `schema/fio_xml_type.xsd`, `schema/responseImportIB.xsd` | Fio's schemas as published at `https://www.fio.cz/schema/`, downloaded 2026-11-26. The written orders and the answers below are validated against them |
| `import-accepted.xml`, `import-refused.xml` | written for these tests from `responseImportIB.xsd` and the error codes in the documentation's section 6: no real answer to an import has been captured yet, so the per-order codes and texts are invented |
