package local.halo.commerce;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Legacy Epay MD5 compatibility protocol; secrets must stay on the server. */
public final class EpayProtocol {
    public String sign(Map<String, String> parameters, String merchantKey) {
        if (merchantKey == null || merchantKey.isBlank()) {
            throw new IllegalArgumentException("Merchant key is required");
        }
        var canonical = new TreeMap<>(parameters).entrySet().stream()
            .filter(entry -> !entry.getKey().equals("sign") && !entry.getKey().equals("sign_type"))
            .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
            .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("&"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                .digest((canonical + merchantKey).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 compatibility algorithm unavailable", e);
        }
    }

    public record Receipt(String tradeNo, String orderNo, long amountMinor, String paymentType) {}

    public Receipt verifySuccess(Map<String, String> parameters, String key, String merchantId,
                                 String expectedOrderNo, long expectedAmountMinor) {
        var supplied = parameters.get("sign");
        if (supplied == null || !supplied.matches("[a-fA-F0-9]{32}")
            || !MessageDigest.isEqual(sign(parameters, key).getBytes(StandardCharsets.US_ASCII),
                supplied.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Invalid payment signature");
        }
        if (!"MD5".equals(parameters.getOrDefault("sign_type", "MD5"))
            || !"TRADE_SUCCESS".equals(parameters.get("trade_status"))
            || merchantId == null || !merchantId.equals(parameters.get("pid"))
            || expectedOrderNo == null || !expectedOrderNo.equals(parameters.get("out_trade_no"))) {
            throw new IllegalArgumentException("Payment does not match the order");
        }
        long amount = parseMoney(parameters.get("money"));
        if (expectedAmountMinor <= 0 || amount != expectedAmountMinor) {
            throw new IllegalArgumentException("Payment amount does not match the order");
        }
        var tradeNo = parameters.get("trade_no");
        var type = parameters.get("type");
        if (tradeNo == null || tradeNo.isBlank() || type == null || type.isBlank()) {
            throw new IllegalArgumentException("Payment identifiers are missing");
        }
        return new Receipt(tradeNo, expectedOrderNo, amount, type);
    }

    public long parseMoney(String money) {
        if (money == null || !money.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("Invalid payment amount");
        }
        try {
            var amount = new BigDecimal(money).movePointRight(2).longValueExact();
            if (amount <= 0 || amount > 9007199254740991L) {
                throw new IllegalArgumentException("Payment amount exceeds supported range");
            }
            return amount;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Invalid payment amount", e);
        }
    }

    public String formatMoney(long amountMinor) {
        if (amountMinor <= 0 || amountMinor > 9007199254740991L) {
            throw new IllegalArgumentException("Invalid payment amount");
        }
        return BigDecimal.valueOf(amountMinor, 2).toPlainString();
    }
}
