package qouteall.imm_ptl.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;

/**
 * Regression test for the global portal cleanup event mis-binding.
 * <p>
 * {@link GlobalPortalStorage#init()} used to bind {@code onServerClose()} (which removes every
 * global portal with {@code RemovalReason.UNLOADED_TO_CHUNK}) to
 * {@code ServerTickEvent.Post} instead of the once-at-shutdown
 * {@code de.nick1st.imm_ptl.events.ServerCleanupEvent}. That meant a global portal was deleted
 * on the very next server tick after being created. This test creates a global portal
 * programmatically (mirroring what {@code PortalCommand}'s
 * {@code convert_normal_portal_to_global_portal} / global portal creation subcommands do
 * server-side via {@link GlobalPortalStorage#addPortal(Portal)}), lets the server tick well
 * past a single tick, and asserts the portal is still present in storage and not removed --
 * which would fail every time under the old (tick-bound) cleanup wiring.
 */
@GameTestHolder("immersive_portals_core")
public class GlobalPortalCleanupGameTest {
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty_5x5", timeoutTicks = 150)
    public static void globalPortalSurvivesServerTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Portal portal = Portal.ENTITY_TYPE.create(level);
        helper.assertTrue(portal != null, "Portal entity could not be created");

        portal.setOriginPos(helper.absoluteVec(new Vec3(2.5, 2.5, 2.5)));
        portal.setDestinationDimension(level.dimension());
        portal.setDestination(helper.absoluteVec(new Vec3(2.5, 2.5, 2.5)).add(0, 0, 8));
        portal.setOrientationAndSize(new Vec3(1, 0, 0), new Vec3(0, 1, 0), 2, 2);

        level.addFreshEntity(portal);

        GlobalPortalStorage storage = GlobalPortalStorage.get(level);
        storage.addPortal(portal);

        helper.assertTrue(
            storage.data.contains(portal) && !portal.isRemoved(),
            "Global portal was not registered in GlobalPortalStorage right after addPortal"
        );

        // Under the pre-fix wiring, onServerClose() ran on every ServerTickEvent.Post, so the
        // portal would already be gone well before 100 ticks pass. Under the fix it is only
        // removed at actual server shutdown, so it must still be present here.
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(
                storage.data.contains(portal),
                "Global portal was dropped from GlobalPortalStorage before server shutdown"
                    + " (onServerClose must be bound to ServerCleanupEvent, not every server tick)"
            );
            helper.assertFalse(
                portal.isRemoved(),
                "Global portal entity was removed before server shutdown"
                    + " (onServerClose must be bound to ServerCleanupEvent, not every server tick)"
            );
            helper.succeed();
        });
    }
}
