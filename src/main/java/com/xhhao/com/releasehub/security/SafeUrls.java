package com.xhhao.com.releasehub.security;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

public final class SafeUrls {
    private SafeUrls() { }
    public static URI https(String value) {
        URI uri;
        try { uri = URI.create(value); } catch (RuntimeException e) {
            throw new IllegalArgumentException("请输入有效的 HTTPS 地址");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
            || uri.getUserInfo() != null || uri.getFragment() != null
            || (uri.getPort() != -1 && uri.getPort() != 443))
            throw new IllegalArgumentException("仅支持不含身份信息的 HTTPS 地址（443 端口）");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
            || host.endsWith(".internal") || host.contains("%"))
            throw new IllegalArgumentException("不允许本地网络地址");
        return uri;
    }
    public static String browserLink(String value) {
        try { return https(value).toASCIIString(); } catch (RuntimeException e) { return ""; }
    }
    public static boolean publicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        if (b.length == 4) {
            int a = b[0] & 255, c = b[1] & 255;
            return a != 0 && a != 10 && a != 127 && a < 224
                && !(a == 100 && c >= 64 && c <= 127) && !(a == 169 && c == 254)
                && !(a == 172 && c >= 16 && c <= 31) && !(a == 192 && (c == 168 || c == 0 || c == 2))
                && !(a == 198 && (c == 18 || c == 19 || c == 51)) && !(a == 203 && c == 0);
        }
        // Accept only global unicast IPv6; exclude transition, documentation and unique-local ranges.
        return (b[0] & 0xe0) == 0x20 && !(b[0] == 0x20 && b[1] == 0x01
            && ((b[2] & 255) == 0 || ((b[2] & 255) == 0x0d && (b[3] & 255) == 0xb8)))
            && !(b[0] == 0x20 && b[1] == 0x02);
    }
}
