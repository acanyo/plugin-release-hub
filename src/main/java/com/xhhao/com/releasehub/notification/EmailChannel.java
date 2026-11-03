package com.xhhao.com.releasehub.notification;

import com.xhhao.com.releasehub.config.HubSettings;
import com.xhhao.com.releasehub.model.*;
import com.xhhao.com.releasehub.security.*;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Mono;

@Component
public class EmailChannel implements NotificationChannel {
    private final HubSettings settings;
    private final TokenService tokens;
    private final HaloEmailSender sender;
    public EmailChannel(HubSettings settings, TokenService tokens, HaloEmailSender sender) {
        this.settings = settings; this.tokens = tokens; this.sender = sender;
    }
    @Override public String name() { return "EMAIL"; }
    @Override public Mono<Void> send(Subscription sub, Delivery delivery, Release release, Project project) {
        boolean verification = "VERIFY".equals(delivery.getSpec().getType());
        return settings.mail().flatMap(mail -> {
            if (!mail.isEnabled()) return Mono.error(new NotificationChannel.Failure("邮件渠道已停用"));
            return Mono.zip(settings.general(), tokens.issue(sub, "unsubscribe")).flatMap(values -> {
                var general = values.getT1();
                String unsubscribe = general.baseUrl() + "/releases/unsubscribe?token=" + values.getT2();
                return (verification ? tokens.issue(sub, "verify") : Mono.just(values.getT2())).map(token -> {
                    String url = general.baseUrl() + "/releases/" + (verification ? "verify" : "unsubscribe") + "?token=" + token;
                    String subject = verification ? "确认订阅 · " + general.getBrandName() : "TEST".equals(delivery.getSpec().getType())
                        ? "测试通知 · " + general.getBrandName() : mail.getSubject().replace("{project}", project.displayName())
                            .replace("{version}", release.getSpec().getTag());
                    String detailUrl = release == null ? general.baseUrl() + "/releases" : general.baseUrl() + "/releases/versions/" + release.getMetadata().getName();
                    String content = verification ? "请确认这是你本人的订阅请求。确认后，你将收到所选项目的版本更新。链接 24 小时内有效。如果不是你发起的请求，请忽略此邮件。"
                        : release == null ? "这是一封来自版本中心的测试通知，邮件渠道已正常连接。" : release.getSpec().getSummary();
                    String heading = verification ? "确认你的版本订阅" : release == null ? "邮件通知已就绪" : project.displayName() + " " + release.getSpec().getTag();
                    String action = verification ? "确认订阅" : "查看更新";
                    String button = verification ? url : detailUrl;
                    String html = template(general, heading, verification ? "" : mail.getIntroduction(), content,
                        release == null ? "" : release.getSpec().getPublishedAt().toString(), action, button, verification);
                    String plain = heading + "\n\n" + content + "\n\n" + action + ": " + button;
                    return new HaloEmailSender.Content(sub.getSpec().getEmail(), subject, plain, html, delivery.getMetadata().getName(), unsubscribe);
                });
            }).flatMap(sender::send);
        });
    }
    private String template(HubSettings.General g, String title, String introduction, String text, String date, String action, String url, boolean verification) {
        String logo = SafeUrls.browserLink(g.getLogoUrl());
        return "<div lang=\"zh-CN\" style=\"margin:0;background:#f6f7f9;color:#20242c;font-family:Arial,sans-serif\">"
            + "<div style=\"max-width:580px;margin:40px auto;background:#fff;padding:40px;border:1px solid #e5e7eb;border-radius:12px\">"
            + (logo.isBlank() ? "" : "<img width=\"36\" height=\"36\" alt=\"\" src=\"" + escape(logo) + "\">")
            + "<p style=\"color:#64748b\">" + escape(g.getBrandName()) + " · RELEASE NOTES</p><h1 style=\"font-size:24px\">" + escape(title)
            + "</h1><p style=\"color:#64748b\">" + escape(date) + "</p><p>" + escape(introduction) + "</p><p style=\"line-height:1.8\">" + escape(text)
            + "</p><p style=\"margin:32px 0\"><a style=\"background:#4164e8;color:white;padding:12px 20px;border-radius:6px;text-decoration:none\" href=\"" + escape(url) + "\">" + escape(action)
            + "</a></p><hr style=\"border:0;border-top:1px solid #e5e7eb\"><p style=\"font-size:12px;color:#64748b\">"
            + (verification ? "仅在你本人发起订阅时确认。" : "你收到此邮件是因为订阅了版本更新。")
            + "</p></div></div>";
    }
    private String escape(String text) { return HtmlUtils.htmlEscape(text == null ? "" : text); }
}
