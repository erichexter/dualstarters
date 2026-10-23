package games.finn.dualstarters;

import java.util.List;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.player.GeneralPlayerData;
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreManager;
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes;
import com.cobblemon.mod.common.config.starter.StarterCategory;
import com.cobblemon.mod.common.pokemon.Pokemon;

import games.finn.dualstarters.net.DualSelectStartersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side logic for Dual Starters.
 *
 * <p>Receives {@link DualSelectStartersPacket} from the custom picker screen and grants
 * two independent starter Pokemon, but ONLY to a player who has never chosen a starter.
 * The authoritative "new player" gate is {@code !starterSelected && !starterLocked} on
 * Cobblemon's {@link GeneralPlayerData} - re-checked here so a spoofed/duplicate packet
 * cannot grant twice.</p>
 */
public final class DualStarterGrantHandler {

    private DualStarterGrantHandler() {}

    /** NeoForge payload handler entry point (runs on the network thread). */
    public static void handle(DualSelectStartersPacket packet, IPayloadContext context) {
        // Hop onto the server thread before touching world/player state.
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                grant(player, packet);
            }
        });
    }

    /**
     * Grants both starters if (and only if) the player has never chosen one.
     * Returns true if the grant happened.
     */
    public static boolean grant(ServerPlayer player, DualSelectStartersPacket packet) {
        PlayerInstancedDataStoreManager dataManager = Cobblemon.INSTANCE.getPlayerDataManager();
        GeneralPlayerData data = dataManager.getGenericData(player);

        // --- New-player gate (authoritative, server-side). ---
        if (!isNewPlayer(data)) {
            DualStarters.LOGGER.info("[Dual Starters] {} already has a starter (selected={}, locked={}); ignoring dual pick.",
                    player.getGameProfile().getName(), data.getStarterSelected(), data.getStarterLocked());
            // Re-sync so a stale client screen closes cleanly.
            data.sendToPlayer(player);
            return false;
        }

        List<StarterCategory> starterList = Cobblemon.INSTANCE.getStarterHandler().getStarterList(player);

        Pokemon first = roll(starterList, packet.category1(), packet.index1(), player);
        Pokemon second = roll(starterList, packet.category2(), packet.index2(), player);
        if (first == null || second == null) {
            DualStarters.LOGGER.warn("[Dual Starters] invalid pick(s) from {}: [{}#{}], [{}#{}] - granting nothing.",
                    player.getGameProfile().getName(),
                    packet.category1(), packet.index1(), packet.category2(), packet.index2());
            return false;
        }

        // Two separate create() calls => two independently rolled Pokemon,
        // even when the same species was picked twice (different IVs/nature/etc).
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        party.add(first);
        party.add(second);

        // Mark the player as having chosen a starter so this never fires again.
        data.setStarterSelected(true);
        data.setStarterUUID(first.getUuid());
        dataManager.saveSingle(data, PlayerInstancedDataStoreTypes.INSTANCE.getGENERAL());
        data.sendToPlayer(player);

        DualStarters.LOGGER.info("[Dual Starters] granted {} two starters: {} + {}",
                player.getGameProfile().getName(),
                first.getSpecies().getName(), second.getSpecies().getName());
        return true;
    }

    /** A "new player" has never chosen a starter and is not otherwise locked out. */
    public static boolean isNewPlayer(GeneralPlayerData data) {
        return !data.getStarterSelected() && !data.getStarterLocked();
    }

    /**
     * Resolves one (categoryName, index) pick into a freshly created Pokemon.
     * index == category size means "random within that category" (Cobblemon convention).
     * Returns null for an invalid pick.
     */
    private static Pokemon roll(List<StarterCategory> starterList, String categoryName, int index, ServerPlayer player) {
        StarterCategory category = null;
        for (StarterCategory c : starterList) {
            if (c.getName().equals(categoryName)) {
                category = c;
                break;
            }
        }
        if (category == null) {
            return null;
        }
        List<? extends PokemonProperties> pokemon = category.getPokemon();
        if (index < 0 || index > pokemon.size()) {
            return null;
        }
        PokemonProperties properties;
        if (index == pokemon.size()) {
            if (pokemon.isEmpty()) {
                return null;
            }
            properties = pokemon.get((int) (Math.random() * pokemon.size()));
        } else {
            properties = pokemon.get(index);
        }
        return properties.create(player);
    }
}
