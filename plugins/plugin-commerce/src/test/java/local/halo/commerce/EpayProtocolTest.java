package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EpayProtocolTest {
    private final EpayProtocol protocol = new EpayProtocol();
    private final String key = "test-fixture-key";
    @Test void canonicalSigningOmitsEmptyAndSignatureFields() {
        var fields = new HashMap<String, String>();
        fields.put("b", "two"); fields.put("a", "中文 & text"); fields.put("empty", "");
        fields.put("sign", "ignored"); fields.put("sign_type", "MD5");
        assertEquals("159aefd363fa19f50f266ed9e3e1a8d3", protocol.sign(fields, key));
    }
    @Test void acceptsVerifiedReceiptAndRejectsTampering() {
        var fields = notification();
        assertEquals(1999, protocol.verifySuccess(fields, key, "1001", "order-1", 1999).amountMinor());
        fields.put("money", "0.01");
        assertThrows(IllegalArgumentException.class, () -> protocol.verifySuccess(fields, key, "1001", "order-1", 1999));
    }
    @Test void rejectsValidlySignedButMismatchedPayments() {
        var fields = notification();
        assertThrows(IllegalArgumentException.class, () -> protocol.verifySuccess(fields, key, "1002", "order-1", 1999));
        assertThrows(IllegalArgumentException.class, () -> protocol.verifySuccess(fields, key, "1001", "order-2", 1999));
        assertThrows(IllegalArgumentException.class, () -> protocol.verifySuccess(fields, key, "1001", "order-1", 2000));
        fields.put("trade_status", "WAIT_BUYER_PAY"); fields.put("sign", protocol.sign(fields, key));
        assertThrows(IllegalArgumentException.class, () -> protocol.verifySuccess(fields, key, "1001", "order-1", 1999));
    }
    @Test void moneyUsesExactMinorUnits() {
        assertEquals(10, protocol.parseMoney("0.1"));
        assertEquals("0.10", protocol.formatMoney(10));
        for (var value : new String[] { "1.001", "1e2", "-1", "NaN", "0", "999999999999999999999" }) {
            assertThrows(IllegalArgumentException.class, () -> protocol.parseMoney(value));
        }
    }
    private HashMap<String, String> notification() {
        var fields = new HashMap<>(Map.of("pid", "1001", "trade_no", "gateway-1", "out_trade_no", "order-1",
            "type", "alipay", "money", "19.99", "trade_status", "TRADE_SUCCESS", "sign_type", "MD5"));
        fields.put("sign", protocol.sign(fields, key));
        return fields;
    }
}
