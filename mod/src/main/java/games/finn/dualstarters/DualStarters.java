package games.finn.dualstarters;

import com.mojang.logging.LogUtils;
import games.finn.dualstarters.net.DualSelectStartersPacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/**
 * Dual Starters - a Cobblemon add-on that lets brand-new players pick TWO
 * starter Pokemon at once from the normal starter flow.
 *
 * <p>The value here must match the modId in {@code neoforge.mods.toml}.</p>
 */
@Mod(DualStarters.MODID)
public class DualStarters {
    public static final String MODID = "dualstarters";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DualStarters(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[Dual Starters] loading - two-starter Cobblemon add-on");
        modEventBus.addListener(DualStarters::registerPayloads);
    }

    private static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        // Client -> server: the player's two picks from the dual picker screen.
        registrar.playToServer(
                DualSelectStartersPacket.TYPE,
                DualSelectStartersPacket.STREAM_CODEC,
                DualStarterGrantHandler::handle
        );
        LOGGER.info("[Dual Starters] registered dual-starter selection packet");
    }
}
