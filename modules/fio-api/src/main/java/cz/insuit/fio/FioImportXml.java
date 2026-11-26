package cz.insuit.fio;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Element;

/** Fio's import XML both ways: the {@code Import} file sent, and the {@code responseImport} that comes back. */
final class FioImportXml {

    private FioImportXml() {}

    /** The orders as an {@code Import} document, elements in the order importIB.xsd requires. */
    static String write(List<FioDomesticPayment> payments) {
        var out = new StringWriter();
        try {
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("Import");
            xml.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
            xml.writeAttribute(
                    "http://www.w3.org/2001/XMLSchema-instance",
                    "noNamespaceSchemaLocation",
                    "http://www.fio.cz/schema/importIB.xsd");
            xml.writeStartElement("Orders");
            for (var payment : payments) {
                xml.writeStartElement("DomesticTransaction");
                element(xml, "accountFrom", payment.accountFrom());
                element(xml, "currency", payment.currency().getCurrencyCode());
                element(xml, "amount", payment.amount());
                element(xml, "accountTo", payment.accountTo());
                element(xml, "bankCode", payment.bankCode());
                element(xml, "ks", payment.constantSymbol());
                element(xml, "vs", payment.variableSymbol());
                element(xml, "ss", payment.specificSymbol());
                element(xml, "date", payment.date().toString());
                element(xml, "messageForRecipient", payment.messageForRecipient());
                element(xml, "comment", payment.comment());
                xml.writeEndElement();
            }
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
        } catch (XMLStreamException e) {
            // Writing to a StringWriter does no I/O; this is a bug, not a condition.
            throw new IllegalStateException("could not write the import XML", e);
        }
        return out.toString();
    }

    private static void element(XMLStreamWriter xml, String name, @Nullable String value) throws XMLStreamException {
        if (value != null) {
            xml.writeStartElement(name);
            xml.writeCharacters(value);
            xml.writeEndElement();
        }
    }

    /** @throws FioInvalidResponseException when the body is not a {@code responseImport} this library can read */
    static FioImportResult parse(String body) {
        var root = FioError.root(body);
        if (root == null || !root.getTagName().equals("responseImport")) {
            throw new FioInvalidResponseException("the import's answer is not Fio's responseImport", null);
        }
        var result = child(root, "result");
        var errorCode = result == null ? null : FioError.text(result, "errorCode");
        var status = result == null ? null : FioError.text(result, "status");
        if (result == null || errorCode == null || status == null) {
            throw new FioInvalidResponseException("the import's answer has no errorCode or status", null);
        }
        var instruction = FioError.text(result, "idInstruction");
        var messages = new ArrayList<String>();
        for (var message : children(result, "message")) {
            var text = message.getTextContent().strip();
            if (!text.isEmpty()) {
                messages.add(text);
            }
        }
        var orderMessages = new ArrayList<FioImportResult.OrderMessage>();
        var details = child(root, "ordersDetails");
        if (details != null) {
            for (var detail : children(details, "detail")) {
                var orderId = number(detail.getAttribute("id"));
                var list = child(detail, "messages");
                for (var message : list == null ? List.<Element>of() : children(list, "message")) {
                    var code = number(message.getAttribute("errorCode"));
                    var messageStatus = message.getAttribute("status");
                    orderMessages.add(new FioImportResult.OrderMessage(
                            orderId,
                            messageStatus.isEmpty() ? null : messageStatus,
                            code == null ? null : Math.toIntExact(code),
                            message.getTextContent().strip()));
                }
            }
        }
        try {
            return new FioImportResult(
                    Integer.parseInt(errorCode),
                    status,
                    instruction == null ? null : Long.valueOf(instruction),
                    messages,
                    orderMessages);
        } catch (NumberFormatException e) {
            throw new FioInvalidResponseException("the import's answer holds a number this library cannot read", e);
        }
    }

    private static @Nullable Element child(Element parent, String tag) {
        var found = children(parent, tag);
        return found.isEmpty() ? null : found.getFirst();
    }

    /** Direct children only: {@code message} is both in {@code result} and inside each {@code detail}. */
    private static List<Element> children(Element parent, String tag) {
        var found = new ArrayList<Element>();
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && element.getTagName().equals(tag)) {
                found.add(element);
            }
        }
        return found;
    }

    private static @Nullable Long number(String value) {
        try {
            return value.isBlank() ? null : Long.valueOf(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
