package games.finn.dualstarters.client;

import java.util.ArrayList;
import java.util.List;

import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.config.starter.RenderableStarterCategory;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;

import games.finn.dualstarters.net.DualSelectStartersPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Dual Starters picker, styled after Cobblemon's own starter screen (per Finn's mockup):
 * a region list down the left, a green detail panel on the right showing the highlighted
 * Pokemon as a live 3D model, and TWO team slots. You pick to fill slot 1, pick again to
 * fill slot 2, then "Confirm your team".
 *
 * <p>STAGE 1 of the redesign: real Cobblemon frame textures + the 3D {@link ModelWidget},
 * region navigation, two team slots. Type icons, the pokedex description, and the scrolling
 * icon row come in later stages. Reuses Cobblemon's runtime assets (MPL-2.0) by resource
 * reference only; no Cobblemon source is copied here.</p>
 */
public class DualStarterSelectScreen extends Screen {

    private static ResourceLocation cb(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/starterselection/" + path);
    }

    private static final ResourceLocation TEX_BACKGROUND = cb("background.png");   // 118x100 model viewport
    private static final ResourceLocation TEX_PLATFORM = cb("platform_base.png");  // 113x32 platform

    /** One chosen team member: the (category, index) the server needs, plus a display name. */
    private record Pick(String categoryName, int index, Component displayName) {}

    private final List<RenderableStarterCategory> categories;
    private int currentCategory = 0;
    private int currentIndex = 0;

    private Pick slot1 = null;
    private Pick slot2 = null;

    private ModelWidget modelWidget;
    private Button confirmButton;

    // Layout (computed in init from the window size).
    private int rootX, rootY;
    private static final int LIST_W = 92;
    private static final int GAP = 6;
    private static final int PANEL_W = 150;
    private static final int PANEL_H = 184;

    public DualStarterSelectScreen(List<RenderableStarterCategory> categories) {
        super(Component.translatable("dualstarters.screen.title"));
        this.categories = categories != null ? categories : new ArrayList<>();
    }

    public boolean hasOptions() {
        for (RenderableStarterCategory c : categories) {
            if (c.getPokemon() != null && !c.getPokemon().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private List<RenderablePokemon> currentMons() {
        if (categories.isEmpty()) {
            return new ArrayList<>();
        }
        List<RenderablePokemon> mons = categories.get(currentCategory).getPokemon();
        return mons != null ? mons : new ArrayList<>();
    }

    private RenderablePokemon currentPokemon() {
        List<RenderablePokemon> mons = currentMons();
        if (mons.isEmpty()) {
            return null;
        }
        if (currentIndex < 0 || currentIndex >= mons.size()) {
            currentIndex = 0;
        }
        return mons.get(currentIndex);
    }

    private Component currentName() {
        RenderablePokemon p = currentPokemon();
        return p != null ? p.getSpecies().getTranslatedName() : Component.literal("-");
    }

    @Override
    protected void init() {
        int totalW = LIST_W + GAP + PANEL_W;
        rootX = (this.width - totalW) / 2;
        rootY = (this.height - PANEL_H) / 2;

        if (!hasOptions()) {
            addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                    .bounds(this.width / 2 - 50, this.height / 2 + 40, 100, 20).build());
            return;
        }

        // ---- Left: region list ----
        int ry = rootY;
        for (int i = 0; i < categories.size(); i++) {
            final int idx = i;
            RenderableStarterCategory cat = categories.get(i);
            boolean selected = (i == currentCategory);
            Component label = cat.getDisplayNameText();
            addRenderableWidget(Button.builder(label, b -> selectCategory(idx))
                    .bounds(rootX, ry, LIST_W, 18).build())
                    .active = !selected;
            ry += 20;
        }

        // ---- Right: detail panel ----
        int panelX = rootX + LIST_W + GAP;
        int modelX = panelX + 16;
        int modelY = rootY + 24;

        try {
            RenderablePokemon cur = currentPokemon();
            if (cur != null) {
                modelWidget = new ModelWidget(modelX, modelY, 118, 100, cur, 2.1f, 0f, -10.0, true, false, 0);
                addRenderableWidget(modelWidget);
            }
        } catch (Throwable t) {
            // Model rendering is best-effort in Stage 1; the rest of the screen still works.
            modelWidget = null;
        }

        // species cycle arrows, centered under the model
        int arrowsY = modelY + 104;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(-1))
                .bounds(panelX + 10, arrowsY, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> cycle(+1))
                .bounds(panelX + PANEL_W - 30, arrowsY, 20, 20).build());

        // "I choose you!" -> add current pick to the next empty slot
        addRenderableWidget(Button.builder(Component.translatable("dualstarters.screen.confirm"), b -> addToTeam())
                .bounds(panelX + 35, arrowsY, PANEL_W - 70, 20).build());

        // team slots + confirm, along the bottom
        int bottomY = rootY + PANEL_H - 24;
        confirmButton = Button.builder(Component.literal("Confirm your team"), b -> confirm())
                .bounds(rootX, bottomY, totalW, 20).build();
        confirmButton.active = (slot1 != null && slot2 != null);
        addRenderableWidget(confirmButton);
    }

    private void selectCategory(int idx) {
        currentCategory = idx;
        currentIndex = 0;
        refreshModel();
        rebuildWidgets();
    }

    private void cycle(int delta) {
        int size = currentMons().size();
        if (size == 0) {
            return;
        }
        currentIndex = (currentIndex + (delta % size) + size) % size;
        refreshModel();
    }

    private void refreshModel() {
        RenderablePokemon cur = currentPokemon();
        if (modelWidget != null && cur != null) {
            try {
                modelWidget.setPokemon(cur);
            } catch (Throwable ignored) {
            }
        }
    }

    private void addToTeam() {
        RenderablePokemon cur = currentPokemon();
        if (cur == null) {
            return;
        }
        Pick pick = new Pick(categories.get(currentCategory).getName(), currentIndex, currentName());
        if (slot1 == null) {
            slot1 = pick;
        } else if (slot2 == null) {
            slot2 = pick;
        } else {
            // Both full: replace slot 2 so the latest pick wins.
            slot2 = pick;
        }
        if (confirmButton != null) {
            confirmButton.active = (slot1 != null && slot2 != null);
        }
    }

    private void confirm() {
        if (slot1 == null || slot2 == null) {
            return;
        }
        PacketDistributor.sendToServer(
                new DualSelectStartersPacket(slot1.categoryName(), slot1.index(),
                        slot2.categoryName(), slot2.index()));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Flat dim behind (no vanilla blur).
        g.fill(0, 0, this.width, this.height, 0xC00A0A12);

        int centerX = this.width / 2;
        g.drawCenteredString(this.font, this.title, centerX, Math.max(4, rootY - 14), 0xFFFFFFFF);

        if (!hasOptions()) {
            g.drawCenteredString(this.font, Component.literal("No starters are configured."),
                    centerX, this.height / 2, 0xFFFF5555);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        int panelX = rootX + LIST_W + GAP;

        // Green detail panel frame (Cobblemon-style), drawn with fills.
        drawPanel(g, panelX, rootY, PANEL_W, PANEL_H);

        // Model viewport background + platform under the 3D model.
        int modelX = panelX + 16;
        int modelY = rootY + 24;
        g.blit(TEX_BACKGROUND, modelX, modelY, 0, 0, 118, 100, 118, 100);
        g.blit(TEX_PLATFORM, modelX + 2, modelY + 72, 0, 0, 113, 32, 113, 32);

        // (ModelWidget renders itself as a child widget, on top of the viewport.)

        // Species name, centered in the panel header.
        g.drawCenteredString(this.font, currentName(), panelX + PANEL_W / 2, rootY + 8, 0xFFFFFFFF);

        // Two team slots just above the confirm bar.
        int slotsY = rootY + PANEL_H - 50;
        drawTeamSlot(g, panelX + 8, slotsY, (PANEL_W - 24) / 2,
                Component.translatable("dualstarters.screen.slot1"), slot1);
        drawTeamSlot(g, panelX + 16 + (PANEL_W - 24) / 2, slotsY, (PANEL_W - 24) / 2,
                Component.translatable("dualstarters.screen.slot2"), slot2);

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Cobblemon-green framed panel: green border, dark interior. */
    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF3BA55D);       // outer green border
        g.fill(x, y, x + w, y + h, 0xF0102014);                       // dark green interior
        g.fill(x, y, x + w, y + 1, 0xFF5BD47E);                       // top highlight
    }

    private void drawTeamSlot(GuiGraphics g, int x, int y, int w, Component label, Pick pick) {
        int top = y, bot = y + 42;
        g.fill(x, top, x + w, bot, 0xFF182818);
        g.fill(x, top, x + w, top + 1, 0xFF3BA55D);
        g.fill(x, bot - 1, x + w, bot, 0xFF3BA55D);
        g.fill(x, top, x + 1, bot, 0xFF3BA55D);
        g.fill(x + w - 1, top, x + w, bot, 0xFF3BA55D);
        int cx = x + w / 2;
        g.drawCenteredString(this.font, label, cx, top + 5, 0xFFBFE8C7);
        Component val = pick != null ? pick.displayName() : Component.literal("- empty -");
        int col = pick != null ? 0xFFFFFFFF : 0xFF808080;
        g.drawCenteredString(this.font, val, cx, top + 24, col);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
