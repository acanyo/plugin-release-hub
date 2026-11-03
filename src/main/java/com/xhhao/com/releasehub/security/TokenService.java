package com.xhhao.com.releasehub.security;

import com.xhhao.com.releasehub.model.HubStore;
import com.xhhao.com.releasehub.model.Subscription;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Secret;

@Component
public class TokenService {
    private final Mono<String> key;
    public TokenService(HubStore store, SecretStore secrets) {
        key = Mono.defer(() -> store.client.fetch(Secret.class, "release-hub-signing-key")
            .flatMap(s -> secrets.read("release-hub-signing-key", "key"))
            .switchIfEmpty(Mono.defer(() -> secrets.put("release-hub-signing-key", "key", random())
                .then(secrets.read("release-hub-signing-key", "key"))))).cacheInvalidateIf(value -> false);
    }
    public static String random() {
        byte[] b = new byte[32]; new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
    public Mono<String> issue(Subscription sub, String purpose) {
        String payload = sub.getMetadata().getName() + ":" + purpose + ":"
            + Instant.now().getEpochSecond() + ":" + sub.getSpec().getTokenVersion();
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return key.map(k -> encoded + "." + sign(k, encoded));
    }
    public Mono<String[]> parse(String token, String purpose) {
        return key.map(k -> {
            try {
                if (token == null || token.length() > 1000) throw invalid();
                String[] parts = token.split("\\.");
                if (parts.length != 2 || !MessageDigest.isEqual(sign(k, parts[0]).getBytes(StandardCharsets.UTF_8),
                    parts[1].getBytes(StandardCharsets.UTF_8))) throw invalid();
                String[] values = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split(":");
                if (values.length != 4 || !values[1].equals(purpose)) throw invalid();
                long age = Instant.now().getEpochSecond() - Long.parseLong(values[2]);
                if (age < -60 || (purpose.equals("verify") && age > 86400)) throw invalid();
                return values;
            } catch (RuntimeException e) { throw invalid(); }
        });
    }
    public static String sign(String key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public static IllegalArgumentException invalid() { return new IllegalArgumentException("链接无效或已过期，请重新订阅"); }
}
