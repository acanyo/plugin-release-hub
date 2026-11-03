package com.xhhao.com.releasehub.web;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.context.IWebContext;
import org.thymeleaf.model.AttributeValueQuotes;
import org.thymeleaf.model.IModel;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.PluginContext;
import run.halo.app.theme.dialect.TemplateHeadProcessor;

@Component
public class ReleaseHubHeadProcessor implements TemplateHeadProcessor {
    private final PluginContext plugin;
    public ReleaseHubHeadProcessor(PluginContext plugin) { this.plugin = plugin; }
    @Override public Mono<Void> process(ITemplateContext context, IModel model, IElementModelStructureHandler handler) {
        var factory = context.getModelFactory();
        if (context instanceof IWebContext web) {
            String action = web.getExchange().getRequest().getParameterValue("rh-action");
            if ("verify".equals(action) || "unsubscribe".equals(action)) {
                // Insert before theme assets so confirmation tokens cannot enter their referrers.
                model.insert(0, factory.createStandaloneElementTag("meta",
                    Map.of("name", "referrer", "content", "no-referrer"), AttributeValueQuotes.DOUBLE, false, true));
                model.insert(1, factory.createStandaloneElementTag("meta",
                    Map.of("name", "robots", "content", "noindex, nofollow"), AttributeValueQuotes.DOUBLE, false, true));
            }
        }
        model.add(factory.createOpenElementTag("script", Map.of("defer", "defer", "src",
            "/plugins/" + plugin.getName() + "/assets/embed/loader.js?v=" + plugin.getVersion()), AttributeValueQuotes.DOUBLE, false));
        model.add(factory.createCloseElementTag("script"));
        return Mono.empty();
    }
}
