package io.kamax.matrix.bridge.voip;

import io.kamax.matrix.bridge.voip.remote.call.FreeswitchEndpoint;
import org.junit.Before;
import org.junit.Test;

import java.util.Objects;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assume.assumeTrue;

public class FreeswitchEndpointTest {

    private String wsUrl = System.getenv("FREESWITCH_VERTO_WS_URL");
    private String wsLogin = System.getenv("FREESWITCH_VERTO_LOGIN");
    private String wsPass = System.getenv("FREESWITCH_VERTO_PASS");

    @Before
    public void before() {
        assumeTrue(Objects.nonNull(wsUrl));
    }

    @Test
    public void connect() throws InterruptedException {
        FreeswitchEndpoint ep = new FreeswitchEndpoint(wsUrl, wsLogin, wsPass);
        assertTrue(!ep.isClosed());
        ep.close();
        assertTrue(ep.isClosed());
    }

}
