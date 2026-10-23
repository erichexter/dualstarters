package games.finn.dualstarters.net;

import games.finn.dualstarters.DualStarters;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server packet carrying the player's two starter picks.
 *
 * <p>Each pick is a (categoryName, index) pair identifying an entry in Cobblemon's
 * normal starter list (the same list the picker screen was populated from). An index
 * equal to the category size means "random within that category" (mirrors Cobblemon's
 * own {@code chooseStarter} convention).</p>
 */
public record DualSelectStartersPacket(String category1, int index1, String category2, int index2)
        implements CustomPacketPayload {

    public static final Type<DualSelectStartersPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DualStarters.MODID, "select_dual_starters"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DualSelectStartersPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, DualSelectStartersPacket::category1,
                    ByteBufCodecs.VAR_INT, DualSelectStartersPacket::index1,
                    ByteBufCodecs.STRING_UTF8, DualSelectStartersPacket::category2,
                    ByteBufCodecs.VAR_INT, DualSelectStartersPacket::index2,
                    DualSelectStartersPacket::new
            );

    @Override
    public Type<DualSelectStartersPacket> type() {
        return TYPE;
    }
}
