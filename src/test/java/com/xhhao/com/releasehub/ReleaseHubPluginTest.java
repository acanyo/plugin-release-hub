package com.xhhao.com.releasehub;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import run.halo.app.plugin.PluginContext;
import run.halo.app.extension.SchemeManager;
import com.xhhao.com.releasehub.sync.HubWorker;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReleaseHubPluginTest {

    @Mock
    PluginContext context;
    @Mock SchemeManager schemes;
    @Mock HubWorker worker;

    @InjectMocks
    ReleaseHubPlugin plugin;

    @Test
    void contextLoads() {
        plugin.start();
        plugin.stop();
        verify(worker).start();
        verify(worker).stop();
        verify(schemes, times(6)).register(any(Class.class));
        verify(schemes, times(6)).unregister(any());
    }
}
