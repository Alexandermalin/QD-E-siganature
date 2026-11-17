package nc.ui.uapbd.qd.esign;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** NC 现场注册编码仅用于策略表显示。 */
final class QdPolicyCodeCatalog {
    static final String PENDING = "待确认";
    private static final Map<String, Codes> CATALOG = load();

    private QdPolicyCodeCatalog() { }

    static Codes find(String scopeId) { return CATALOG.get(scopeId); }

    private static Map<String, Codes> load() {
        try (InputStream source = QdPolicyCodeCatalog.class.getResourceAsStream("operation-codes.xml")) {
            if (source == null) { throw new IOException("operation-codes.xml is missing"); }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            NodeList bindings = factory.newDocumentBuilder().parse(source).getElementsByTagName("binding");
            Map<String, Codes> catalog = new LinkedHashMap<String, Codes>();
            for (int index = 0; index < bindings.getLength(); index++) {
                Element binding = (Element) bindings.item(index);
                String scope = binding.getAttribute("scope");
                Codes codes = catalog.get(scope);
                if (codes == null) {
                    codes = new Codes();
                    catalog.put(scope, codes);
                }
                codes.add(binding.getAttribute("node"), binding.getAttribute("button"), binding.getAttribute("entry"));
            }
            return catalog;
        } catch (IOException | ParserConfigurationException | SAXException error) {
            throw new IllegalStateException("无法读取电子签名现场注册编码。", error);
        }
    }

    static final class Codes {
        private final List<String> nodes = new ArrayList<String>();
        private final List<String> buttons = new ArrayList<String>();
        private final List<String> pairs = new ArrayList<String>();
        private boolean hasEntry;
        private boolean hasButton;

        private void add(String node, String button, String entry) {
            boolean registered = !button.isEmpty();
            String displayButton = registered ? button : PENDING;
            boolean entryOnly = !registered && !entry.isEmpty();
            nodes.add(node);
            buttons.add(displayButton);
            pairs.add(node + " / " + displayButton + (entryOnly ? "（子页入口 " + entry + "）" : ""));
            hasEntry |= entryOnly;
            hasButton |= registered;
        }

        String nodes() { return join(nodes, " / "); }
        String buttons() { return join(buttons, " / "); }
        String pairs() { return join(pairs, "，"); }
        String status() {
            if (hasButton) { return "已登记，待接入"; }
            return hasEntry ? "已定位子页入口" : "按钮待确认";
        }

        private static String join(List<String> values, String separator) {
            StringBuilder joined = new StringBuilder(values.get(0));
            for (int index = 1; index < values.size(); index++) {
                joined.append(separator).append(values.get(index));
            }
            return joined.toString();
        }
    }
}
