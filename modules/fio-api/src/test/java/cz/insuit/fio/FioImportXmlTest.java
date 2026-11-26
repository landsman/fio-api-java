package cz.insuit.fio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXException;

/** Against Fio's own schemas, so what is written is what Fio validates and the answers have Fio's shape. */
@DisplayName("Fio import XML")
class FioImportXmlTest {

    static final FioDomesticPayment FULL = new FioDomesticPayment(
            "2400222222",
            Currency.getInstance("CZK"),
            12_345,
            "2212-2000000699",
            "0300",
            LocalDate.of(2026, 12, 1),
            "1234567890",
            "0558",
            "42",
            "Refund <order> & \"thanks\", příliš žluťoučký kůň",
            "refund 7");

    private static final FioDomesticPayment MINIMAL = new FioDomesticPayment(
            "2400222222",
            Currency.getInstance("CZK"),
            1,
            "2000000699",
            "0800",
            LocalDate.of(2026, 12, 1),
            null,
            null,
            null,
            null,
            null);

    @Test
    @DisplayName("the written orders pass Fio's importIB.xsd, every optional field present or absent")
    void writtenOrdersMatchFiosSchema() throws Exception {
        validate("importIB.xsd", FioImportXml.write(List.of(FULL, MINIMAL)));
    }

    @Test
    @DisplayName("amounts are written exactly, text escaped and kept")
    void writesValues() {
        var xml = FioImportXml.write(List.of(FULL));

        assertTrue(xml.contains("<amount>123.45</amount>"), xml);
        assertTrue(xml.contains("<ks>0558</ks><vs>1234567890</vs><ss>42</ss>"), xml);
        assertTrue(xml.contains("Refund &lt;order&gt; &amp; \"thanks\", příliš žluťoučký kůň"), xml);
    }

    @Test
    @DisplayName("an accepted batch: its id, status and per-order messages")
    void readsAnAcceptedBatch() throws Exception {
        var body = fixture("import-accepted.xml");
        validate("responseImportIB.xsd", body);

        var result = FioImportXml.parse(body);

        assertTrue(result.accepted());
        assertEquals(0, result.errorCode());
        assertEquals(105909498L, result.instructionId());
        assertEquals(List.of(new FioImportResult.OrderMessage(1L, "ok", 0, "OK")), result.orderMessages());
    }

    @Test
    @DisplayName("a refused batch: no id, the batch's message apart from each order's")
    void readsARefusedBatch() throws Exception {
        var body = fixture("import-refused.xml");
        validate("responseImportIB.xsd", body);

        var result = FioImportXml.parse(body);

        assertFalse(result.accepted());
        assertEquals(1, result.errorCode());
        assertNull(result.instructionId());
        assertEquals(List.of("Nalezené chyby při kontrole příkazů"), result.messages());
        assertEquals(2, result.orderMessages().size());
        assertEquals(
                new FioImportResult.OrderMessage(1L, "error", 1088, "Neplatné číslo účtu příjemce"),
                result.orderMessages().getFirst());
    }

    @Test
    @DisplayName("a per-order code beyond an int is dropped, not thrown")
    void dropsAnOutOfRangeOrderCode() {
        var result = FioImportXml.parse("<responseImport><result><errorCode>1</errorCode><status>error</status>"
                + "</result><ordersDetails><detail id=\"1\"><messages>"
                + "<message status=\"error\" errorCode=\"99999999999\">x</message>"
                + "</messages></detail></ordersDetails></responseImport>");

        assertEquals(List.of(new FioImportResult.OrderMessage(1L, "error", null, "x")), result.orderMessages());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "not xml",
                "<response><result><errorCode>21</errorCode><status>error</status><message>x</message></result></response>",
                "<responseImport><result><status>ok</status></result></responseImport>",
                "<responseImport><result><errorCode>x</errorCode><status>ok</status></result></responseImport>",
            })
    @DisplayName("anything but a readable responseImport is an invalid response")
    void refusesOtherAnswers(String body) {
        assertThrows(FioInvalidResponseException.class, () -> FioImportXml.parse(body));
    }

    private static void validate(String schema, String xml) throws SAXException, IOException {
        var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        // importIB.xsd includes fio_xml_type.xsd beside it, so the schema is read from its URL, not a stream.
        var url = Objects.requireNonNull(FioImportXmlTest.class.getResource("/fio/schema/" + schema));
        factory.newSchema(url).newValidator().validate(new StreamSource(new StringReader(xml)));
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = FioImportXmlTest.class.getResourceAsStream("/fio/" + name)) {
            return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
