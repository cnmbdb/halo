package local.halo.commerce;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class EpayPaymentService {
    private static final Set<String> PAYMENT_TYPES = Set.of("alipay", "wxpay", "usdt", "qqpay", "bank", "jdpay", "paypal", "douyinpay");
    private final EpaySettings settings;
    private final EpayProtocol protocol;

    @Autowired
    public EpayPaymentService(EpaySettings settings) { this(settings, new EpayProtocol()); }
    EpayPaymentService(EpaySettings settings, EpayProtocol protocol) { this.settings = settings; this.protocol = protocol; }

    public Map<String, String> request(CommerceOrder order, String paymentType) {
        if (!settings.configured()) throw new IllegalStateException("Epay settings are not configured");
        settings.submitUrl();
        var spec = order.getSpec();
        if (spec.state() != CommerceOrder.State.AWAITING_PAYMENT) throw new IllegalStateException("Order is not awaiting payment");
        if (!PAYMENT_TYPES.contains(paymentType)) throw new IllegalArgumentException("Unsupported Epay payment type");
        if (!settings.paymentEnabled(paymentType)) throw new IllegalStateException("Payment channel is disabled or not connected");
        var fields = new LinkedHashMap<String, String>();
        fields.put("pid", settings.merchantId()); fields.put("type", paymentType);
        fields.put("out_trade_no", order.getMetadata().getName());
        fields.put("notify_url", settings.notifyUrl()); fields.put("return_url", settings.returnUrl());
        fields.put("name", productName(spec.lines())); fields.put("money", protocol.formatMoney(spec.totalMinor()));
        if (!settings.siteName().isBlank()) fields.put("sitename", truncateUtf8(settings.siteName(), 127));
        fields.put("sign_type", "MD5"); fields.put("sign", protocol.sign(fields, settings.merchantKey()));
        return Map.copyOf(fields);
    }

    public String submitUrl() { return settings.submitUrl(); }
    public EpayProtocol protocol() { return protocol; }

    private String productName(java.util.List<CheckoutPricing.Line> lines) {
        var value = lines.size() == 1 ? lines.getFirst().productTitle() : "商城订单（" + lines.size() + " 项）";
        return truncateUtf8(value, 127);
    }
    private String truncateUtf8(String value, int maxBytes) {
        var result = new StringBuilder(); int bytes = 0;
        for (var point : value.codePoints().toArray()) {
            var token = new String(Character.toChars(point)); var tokenBytes = token.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + tokenBytes > maxBytes) break;
            result.append(token); bytes += tokenBytes;
        }
        return result.toString();
    }
}
