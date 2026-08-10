package qouteall.imm_ptl;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import qouteall.imm_ptl.core.platform_specific.IPConfig;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigDefaultsTest {
    @Test
    public void quarantineSwitchesExistWithSafeDefaults() {
        IPConfig c = new IPConfig();
        assertTrue(c.enableDimensionStack);
        assertFalse(c.disableTeleportation);
        assertTrue(c.enableCrossPortalCollision);
    }

    @Test
    public void safeModpackProfileHasIntendedDeltas() throws IOException {
        // Path is relative to the project dir, which is the test task's working
        // directory for this plain-JVM (no Minecraft runtime) test source set.
        File profileFile = new File("safe-profile/immersive_portals.json");
        assertTrue(
            profileFile.exists(),
            "expected safe-profile/immersive_portals.json at " + profileFile.getAbsolutePath()
        );

        IPConfig profile;
        try (FileReader reader = new FileReader(profileFile)) {
            profile = new Gson().fromJson(reader, IPConfig.class);
        }

        assertEquals(3, profile.maxPortalLayer);
        assertEquals(100, profile.portalRenderLimit);
        assertEquals(4, profile.indirectLoadingRadiusCap);
        assertFalse(profile.enableDimensionStack);
        assertTrue(profile.initialScreenShown);
    }
}
