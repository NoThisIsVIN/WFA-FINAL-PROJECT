package com.metrocitypower.mocks;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.metrocitypower.mocks.esign.HmacSigner;

class HmacSignerTest {

    @Test
    void matchesKnownHmacSha256Vector() {
        // Well-known test vector for HMAC-SHA256.
        String signature = HmacSigner.sign("key",
                "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8));
        assertThat(signature).isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void differentSecretGivesDifferentSignature() {
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        assertThat(HmacSigner.sign("demo-esign-secret", body)).isNotEqualTo(HmacSigner.sign("other", body));
    }
}
