package com.xhhao.com.releasehub;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import run.halo.app.extension.Extension;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import com.xhhao.com.releasehub.model.*;
import com.xhhao.com.releasehub.sync.HubWorker;
import java.util.List;

/**
 * <p>Plugin main class to manage the lifecycle of the plugin.</p>
 * <p>This class must be public and have a public constructor.</p>
 * <p>Only one main class extending {@link BasePlugin} is allowed per plugin.</p>
 *
 * @author Handsome
 * @since 1.0.0
 */
@Component
public class ReleaseHubPlugin extends BasePlugin {
    private final SchemeManager schemes;
    private final HubWorker worker;
    private static final List<Class<? extends Extension>> MODELS = List.of(Project.class, Release.class,
        Subscription.class, Delivery.class, SyncLog.class, RepositoryAccount.class);

    public ReleaseHubPlugin(PluginContext pluginContext, SchemeManager schemes, HubWorker worker) {
        super(pluginContext);
        this.schemes = schemes; this.worker = worker;
    }

    @Override
    public void start() {
        MODELS.forEach(schemes::register);
        worker.start();
    }

    @Override
    public void stop() {
        worker.stop();
        MODELS.forEach(type -> schemes.unregister(Scheme.buildFromType(type)));
    }
}
