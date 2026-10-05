package local.halo.commerce;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EpaySettings {
    private final boolean enabled;
    private final String submitUrl;
    private final String merchantId;
    private final String merchantKey;
    private final String notifyUrl;
    private final String returnUrl;
    private final String siteName;
    private java.util.Set<String> paymentTypes = java.util.Set.of("alipay", "wxpay");
    public EpaySettings withPaymentTypes(java.util.Set<String> types) {
        var result = new EpaySettings(submitUrl, merchantId, merchantKey, notifyUrl, returnUrl, siteName, enabled);
        result.paymentTypes = java.util.Set.copyOf(types);
        return result;
    }
    public boolean paymentEnabled(String type) { return paymentTypes.contains(type); }

    @Autowired
    public EpaySettings(@Value("${commerce.epay.submit-url:}") String submitUrl,
                        @Value("${commerce.epay.merchant-id:}") String merchantId,
                        @Value("${commerce.epay.merchant-key:}") String merchantKey,
                        @Value("${commerce.epay.notify-url:}") String notifyUrl,
                        @Value("${commerce.epay.return-url:}") String returnUrl,
                        @Value("${commerce.epay.site-name:}") String siteName) {
        this(submitUrl, merchantId, merchantKey, notifyUrl, returnUrl, siteName, true);
    }
    public EpaySettings(String submitUrl, String merchantId, String merchantKey, String notifyUrl,
                        String returnUrl, String siteName, boolean enabled) {
        this.enabled = enabled;
        this.submitUrl = value(submitUrl); this.merchantId = value(merchantId); this.merchantKey = value(merchantKey);
        this.notifyUrl = value(notifyUrl); this.returnUrl = value(returnUrl); this.siteName = value(siteName);
    }
    public EpaySettings forLocalRequest(URI requestUri) {
        var host = requestUri.getHost();
        if (!("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
            || "[::1]".equals(host))) return this;
        var origin = requestUri.getScheme() + "://" + requestUri.getRawAuthority();
        return new EpaySettings(submitUrl, merchantId, merchantKey,
            notifyUrl.isBlank() ? origin + "/shop/payment/notify" : notifyUrl,
            returnUrl.isBlank() ? origin + "/shop/orders" : returnUrl, siteName, enabled)
            .withPaymentTypes(paymentTypes);
    }
    private String requireCallback(String value, String field) {
        var uri = URI.create(value);
        if ("http".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
            && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost())
                || "[::1]".equals(uri.getHost()))) return uri.toASCIIString();
        return requireHttps(value, field);
    }
    public boolean configured() {
        if (!enabled || submitUrl.isBlank() || !merchantId.matches("[0-9]{1,24}") || merchantKey.isBlank()
            || notifyUrl.isBlank() || returnUrl.isBlank()) return false;
        try {
            requireHttps(submitUrl, "Submit URL");
            requireCallback(notifyUrl, "Notify URL");
            requireCallback(returnUrl, "Return URL");
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }
    public String submitUrl() {
        var uri = URI.create(requireHttps(submitUrl, "Submit URL"));
        if ((uri.getPath() == null || uri.getPath().isEmpty() || uri.getPath().equals("/"))
            && uri.getQuery() == null && uri.getFragment() == null) {
            return uri.resolve("/submit.php").toASCIIString();
        }
        return uri.toASCIIString();
    }
    public String merchantId() { if (merchantId.isBlank() || !merchantId.matches("[0-9]{1,24}")) throw new IllegalStateException("Merchant ID is not configured"); return merchantId; }
    public String merchantKey() { if (merchantKey.isBlank()) throw new IllegalStateException("Merchant key is not configured"); return merchantKey; }
    public String notifyUrl() { return requireCallback(notifyUrl, "Notify URL"); }
    public String returnUrl() { return requireCallback(returnUrl, "Return URL"); }
    public String siteName() { return siteName; }
    private String requireHttps(String value, String field) {
        try {
            var uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || value.length() > 2048) throw new IllegalStateException(field + " must be a public HTTPS URL");
            return uri.toASCIIString();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(field + " is invalid", e);
        }
    }
    private static String value(String value) { return value == null ? "" : value.trim(); }
}
