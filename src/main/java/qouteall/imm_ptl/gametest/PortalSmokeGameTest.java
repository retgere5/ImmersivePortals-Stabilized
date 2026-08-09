package qouteall.imm_ptl.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * First automated in-game safety net for this fork: spawns a {@link Portal} entity
 * server-side with a fully-specified transform and asserts it becomes valid.
 * <p>
 * Uses a structure-less (empty, no blocks) 5x5x5 template since NeoForge 21.1.152 has no
 * {@code @EmptyTemplate}-style annotation to skip templates entirely
 * (see {@code src/main/resources/data/immersive_portals_core/structure/empty_5x5.nbt}).
 * {@code @PrefixGameTestTemplate(false)} disables the default class-name-prefixing of the
 * template lookup, so the structure resolves to {@code immersive_portals_core:empty_5x5}
 * instead of {@code immersive_portals_core:portalsmokegametest.empty_5x5}.
 */
@GameTestHolder("immersive_portals_core")
public class PortalSmokeGameTest {
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void portalEntitySpawns(GameTestHelper helper) {
        Portal portal = Portal.ENTITY_TYPE.create(helper.getLevel());
        helper.assertTrue(portal != null, "Portal entity could not be created");

        portal.setOriginPos(helper.absoluteVec(new Vec3(2.5, 2.5, 2.5)));
        portal.setDestinationDimension(helper.getLevel().dimension());
        portal.setDestination(helper.absoluteVec(new Vec3(2.5, 2.5, 2.5)).add(0, 0, 8));
        portal.setOrientationAndSize(new Vec3(1, 0, 0), new Vec3(0, 1, 0), 2, 2);

        helper.getLevel().addFreshEntity(portal);

        helper.succeedWhen(() -> helper.assertTrue(portal.isPortalValid(), "Portal invalid after spawn"));
    }
}
