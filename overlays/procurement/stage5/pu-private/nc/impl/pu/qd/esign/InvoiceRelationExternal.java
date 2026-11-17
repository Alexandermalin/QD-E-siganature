package nc.impl.pu.qd.esign;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import nc.pu.m25.util.ReadPropertiesUtil;
import nc.vo.pub.BusinessException;
import org.json.JSONArray;
import org.json.JSONObject;

/** The configured 齐都 updateInvoiceCon contract, with bounded I/O and explicit receipt classification. */
final class InvoiceRelationExternal {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private final URL endpoint;
    private final byte[] payload;
    private final String[] numbers;

    InvoiceRelationExternal(String payload, String[] numbers) throws BusinessException {
        try {
            String configured = ReadPropertiesUtil.getInstance().getNcConfigField("updateInvoiceCon");
            endpoint = new URL(configured);
            if (!("http".equals(endpoint.getProtocol()) || "https".equals(endpoint.getProtocol()))
                    || endpoint.getHost().isEmpty() || endpoint.getUserInfo() != null) {
                throw new IllegalArgumentException("invalid endpoint");
            }
        } catch (Exception ex) {
            throw new BusinessException("NC 数电发票回传地址配置无效。");
        }
        this.payload = payload.getBytes(StandardCharsets.UTF_8);
        this.numbers = numbers.clone();
    }

    String call() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            if (connection.getResponseCode() != 200) return "EXTERNAL_UNKNOWN";
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream response = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int size;
                while ((size = input.read(buffer)) != -1) {
                    if (response.size() + size > MAX_RESPONSE_BYTES) return "EXTERNAL_UNKNOWN";
                    response.write(buffer, 0, size);
                }
                return classify(new String(response.toByteArray(), StandardCharsets.UTF_8), numbers);
            }
        } finally {
            connection.disconnect();
        }
    }

    static String classify(String response, String[] expected) {
        try {
            JSONArray result = new JSONObject(response).getJSONArray("result");
            if (expected.length == 0 || result.length() != expected.length) return "EXTERNAL_UNKNOWN";
            Set<String> seen = new HashSet<String>();
            Set<String> allowed = new HashSet<String>();
            for (String number : expected) allowed.add(number);
            int accepted = 0;
            int rejected = 0;
            for (int i = 0; i < result.length(); i++) {
                JSONObject item = result.getJSONObject(i);
                String number = item.optString("fphm", "");
                if (!number.isEmpty() && (!allowed.contains(number) || !seen.add(number))) return "EXTERNAL_UNKNOWN";
                String code = item.getString("code");
                if ("E000".equals(code)) accepted++;
                else if ("E001".equals(code)) rejected++;
                else return "EXTERNAL_UNKNOWN";
            }
            if (!seen.isEmpty() && seen.size() != expected.length) return "EXTERNAL_UNKNOWN";
            if (accepted == expected.length) return "EXTERNAL_ACCEPTED";
            if (rejected == expected.length) return "EXTERNAL_REJECTED";
        } catch (Exception ex) {
            return "EXTERNAL_UNKNOWN";
        }
        return "EXTERNAL_UNKNOWN";
    }
}
