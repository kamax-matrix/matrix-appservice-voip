package io.kamax.matrix.bridge.voip;

import io.kamax.matrix.bridge.voip.remote.call.FreeswitchManager;
import org.junit.Before;
import org.junit.Test;

import java.util.Objects;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assume.assumeTrue;

public class FreeswitchEndpointTest {

    @Before
    public void before() {
        assumeTrue(Objects.nonNull(System.getenv("FREESWITCH_TEST_ENABLED")));
    }

    @Test
    public void connect() throws InterruptedException {
        FreeswitchManager ep = new FreeswitchManager();
        assertTrue(!ep.isClosed());
        ep.close();
        assertTrue(ep.isClosed());
    }

}
