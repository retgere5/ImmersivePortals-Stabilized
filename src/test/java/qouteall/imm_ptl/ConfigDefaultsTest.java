package qouteall.imm_ptl;

import org.junit.jupiter.api.Test;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import static org.junit.jupiter.api.Assertions.*;

public class ConfigDefaultsTest {
    @Test
    public void quarantineSwitchesExistWithSafeDefaults() {
        IPConfig c = new IPConfig();
        assertTrue(c.enableDimensionStack);
        assertFalse(c.disableTeleportation);
        assertTrue(c.enableCrossPortalCollision);
    }
}
