package cz.insuit.fio;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * What Fio said about a failed request, when it said it in its own shape:
 * {@code <response><result><errorCode>21</errorCode><status>error</status><message>Výpis neexistuje</message>
 * </result></response>}. Fio documents no list of codes, so the message is the part to show a person.
 *
 * @param code Fio's error code, such as {@code 21}; null when the body had none
 * @param status Fio's status word, such as {@code error}; null when the body had none
 */
public record FioError(@Nullable Integer code, @Nullable String status, String message) {

    public FioError {
        requireNonNull(message, "message");
    }

    /** The error in {@code body}, or null when the body is not Fio's error document. Never throws. */
    static @Nullable FioError parse(@Nullable String body) {
        if (body == null || !body.stripLeading().startsWith("<")) {
            return null;
        }
        try {
            var factory = DocumentBuilderFactory.newInstance();
            // The body comes from the network: no DTD, so no external entity can be read through it.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            // The JDK's default handler prints every malformed body to stderr; this one only throws.
            builder.setErrorHandler(new DefaultHandler());
            var root = builder.parse(new InputSource(new StringReader(body))).getDocumentElement();
            var message = text(root, "message");
            if (message == null) {
                return null;
            }
            var code = text(root, "errorCode");
            return new FioError(code == null ? null : parseCode(code), text(root, "status"), message);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            return null;
        }
    }

    private static @Nullable String text(Element root, String tag) {
        var nodes = root.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        var value = nodes.item(0).getTextContent().strip();
        return value.isEmpty() ? null : value;
    }

    private static @Nullable Integer parseCode(String code) {
        try {
            return Integer.valueOf(code);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
