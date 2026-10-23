package games.finn.dualstarters.client;

import java.util.ArrayList;
import java.util.List;

import com.cobblemon.mod.common.config.starter.RenderableStarterCategory;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;

import games.finn.dualstarters.net.DualSelectStartersPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Dual Starters picker: two starter slots side by side, each cycling through
 * the normal Cobblemon starter list, followed by a "Confirm your team" step.
 *
 * <p>Both picks come from the same list; the same species may be picked in both
 * slots. Closing without confirming sends nothing. On confirm, a
 * {@link DualSelectStartersPacket} is sent to the server, which performs the
 * authoritative new-player check and grants two independently rolled Pokemon.</p>
 *
 * <p>Note: this is a functional, Cobblemon-flavored layout built from vanilla
 * widgets. Exact texture/3D-model fidelity with Cobblemon's own screen is a
 * client-side polish pass and needs a real display to iterate on.</p>
 */
public class DualStarterSelectScreen extends Screen {

    /** One selectable starter: a (category, index-within-category) pair plus its display name. */
    private record Option(String categoryName, int index, Component displayName) {}

    private enum Phase { SELECT, CONFIRM }

    private final List<Option> options = new ArrayList<>();
    private int slot1 = 0;
    private int slot2 = 0;
    private Phase phase = Phase.SELECT;

    public DualStarterSelectScreen(List<RenderableStarterCategory> categories) {
        super(Component.translatable("dualstarters.screen.title"));
        for (RenderableStarterCategory category : categories) {
            List<RenderablePokemon> mons = category.getPokemon();
            for (int i = 0; i < mons.size(); i++) {
                options.add(new Option(category.getName(), i, mons.get(i).getSpecies().getTranslatedName()));
            }
        }
    }

    public boolean hasOptions() {
        return !options.isEmpty();
    }

    @Override
    protected void init() {
        if (options.isEmpty()) {
            // Nothing to show; let the player close out (grants nothing).
            addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                    .bounds(this.width / 2 - 50, this.height / 2 + 40, 100, 20).build());
            return;
        }

        int centerX = this.width / 2;
        int slotTop = this.height / 2 - 20;
        int slotW = 120;
        int gap = 20;
        int leftX = centerX - gap / 2 - slotW;
        int rightX = centerX + gap / 2;

        if (phase == Phase.SELECT) {
            // Slot 1 navigation
            addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(0, -1))
                    .bounds(leftX, slotTop, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> cycle(0, +1))
                    .bounds(leftX + slotW - 20, slotTop, 20, 20).build());
            // Slot 2 navigation
            addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(1, -1))
                    .bounds(rightX, slotTop, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> cycle(1, +1))
                    .bounds(rightX + slotW - 20, slotTop, 20, 20).build());
            // Confirm-your-team step entry
            addRenderableWidget(Button.builder(Component.translatable("dualstarters.screen.confirm"), b -> {
                        phase = Phase.CONFIRM;
                        rebuildWidgets();
                    })
                    .bounds(centerX - 75, this.height - 48, 150, 20).build());
        } else {
            // Confirmation step: lock in or go back.
            addRenderableWidget(Button.builder(Component.literal("Confirm"), b -> confirm())
                    .bounds(centerX - 105, this.height - 48, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
                        phase = Phase.SELECT;
                        rebuildWidgets();
                    })
                    .bounds(centerX + 5, this.height - 48, 100, 20).build());
        }
    }

    private void cycle(int slot, int delta) {
        int size = options.size();
        if (slot == 0) {
            slot1 = (slot1 + delta % size + size) % size;
        } else {
            slot2 = (slot2 + delta % size + size) % size;
        }
    }

    private void confirm() {
        Option a = options.get(slot1);
        Option b = options.get(slot2);
        PacketDistributor.sendToServer(
                new DualSelectStartersPacket(a.categoryName(), a.index(), b.categoryName(), b.index()));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        g.drawCenteredString(this.font, this.title, centerX, 24, 0xFFFFFF);

        if (options.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal("No starters are configured."),
                    centerX, this.height / 2, 0xFF5555);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        int slotTop = this.height / 2 - 20;
        int slotW = 120;
        int gap = 20;
        int leftX = centerX - gap / 2 - slotW;
        int rightX = centerX + gap / 2;

        drawSlot(g, leftX, slotTop, slotW, Component.translatable("dualstarters.screen.slot1"),
                options.get(slot1).displayName());
        drawSlot(g, rightX, slotTop, slotW, Component.translatable("dualstarters.screen.slot2"),
                options.get(slot2).displayName());

        if (phase == Phase.CONFIRM) {
            g.drawCenteredString(this.font,
                    Component.translatable("dualstarters.screen.confirm"), centerX, this.height - 70, 0xFFFF55);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawSlot(GuiGraphics g, int x, int y, int w, Component label, Component species) {
        // Framed panel behind the slot.
        g.fill(x, y - 22, x + w, y + 46, 0xC0101018);
        g.fill(x, y - 22, x + w, y - 21, 0xFF404058);
        g.fill(x, y + 45, x + w, y + 46, 0xFF404058);
        int cx = x + w / 2;
        g.drawCenteredString(this.font, label, cx, y - 18, 0xAAB0FF);
        g.drawCenteredString(this.font, species, cx, y + 26, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
