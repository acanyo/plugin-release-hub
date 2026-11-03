package com.xhhao.com.releasehub.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.context.IWebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.IWebRequest;
import run.halo.app.plugin.PluginContext;

class ReleaseHubHeadProcessorTest {
    private String head(String action) {
        var context = mock(ITemplateContext.class, withSettings().extraInterfaces(IWebContext.class));
        var exchange = mock(IWebExchange.class);
        var request = mock(IWebRequest.class);
        when(((IWebContext) context).getExchange()).thenReturn(exchange);
        when(exchange.getRequest()).thenReturn(request);
        when(request.getParameterValue("rh-action")).thenReturn(action);
        var factory = new SpringTemplateEngine().getConfiguration().getModelFactory(TemplateMode.HTML);
        when(context.getModelFactory()).thenReturn(factory);
        var model = factory.createModel(factory.createText("<script src=\"/theme.js\"></script>"));
        var plugin = mock(PluginContext.class);
        when(plugin.getName()).thenReturn("release-hub");
        when(plugin.getVersion()).thenReturn("1.0.0");
        new ReleaseHubHeadProcessor(plugin).process(context, model, null).block();
        return model.toString();
    }

    @Test void tokenActionsSetPrivacyBeforeAnyThemeAssets() {
        for (String action : java.util.List.of("verify", "unsubscribe")) {
            String html = head(action);
            assertTrue(html.contains("no-referrer"));
            assertTrue(html.indexOf("no-referrer") < html.indexOf("/theme.js"));
            assertTrue(html.contains("noindex, nofollow"));
            assertTrue(html.contains("/plugins/release-hub/assets/embed/loader.js?v=1.0.0"));
        }
    }

    @Test void ordinaryPagesKeepTheirOwnReferrerAndIndexingPolicy() {
        String html = head(null);
        assertFalse(html.contains("name=\"referrer\""));
        assertFalse(html.contains("name=\"robots\""));
        assertTrue(html.contains("loader.js?v=1.0.0"));
    }
}
