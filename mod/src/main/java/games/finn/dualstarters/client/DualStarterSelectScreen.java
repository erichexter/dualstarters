package games.finn.dualstarters.client;

import java.util.ArrayList;
import java.util.List;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.gui.TypeIcon;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.config.starter.RenderableStarterCategory;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;

import games.finn.dualstarters.net.DualSelectStartersPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Dual Starters picker, styled after Cobblemon's native starter screen (per Finn's mockup):
 * a region list down the left, a green detail panel on the right with the highlighted Pokemon as
 * a live 3D model, and TWO team slots. Pick to fill slot 1, pick again to fill slot 2, then
 * "Confirm your team".
 *
 * <p>Layout is adaptive to the scaled GUI size (this.width/this.height), top-aligned so the full
 * region list fits. Reuses Cobblemon runtime assets (MPL-2.0) by resource reference only.</p>
 */
public class DualStarterSelectScreen extends Screen {

    private static ResourceLocation cb(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/starterselection/" + path);
    }

    private static final ResourceLocation TEX_BACKGROUND = cb("background.png");   // 118x100 viewport
    private static final ResourceLocation TEX_PLATFORM = cb("platform_base.png");  // 113x32 platform

    private record Pick(String categoryName, int index, Component displayName) {}

    private final List<RenderableStarterCategory> categories;
    private int currentCategory = 0;
    private int currentIndex = 0;

    private Pick slot1 = null;
    private Pick slot2 = null;

    private ModelWidget modelWidget;
    private Button confirmButton;

    private int rootX, rootY, panelH, panelX;
    private static final int LIST_W = 84;
    private static final int GAP = 6;
    private static final int PANEL_W = 150;

    public DualStarterSelectScreen(List<RenderableStarterCategory> categories) {
        super(Component.translatable("dualstarters.screen.title"));
        this.categories = categories != null ? categories : new ArrayList<>();
    }

    public boolean hasOptions() {
        for (RenderableStarterCategory c : categories) {
            if (c.getPokemon() != null && !c.getPokemon().isEmpty()) return true;
        }
        return false;
    }

    private List<RenderablePokemon> currentMons() {
        if (categories.isEmpty()) return new ArrayList<>();
        List<RenderablePokemon> mons = categories.get(currentCategory).getPokemon();
        return mons != null ? mons : new ArrayList<>();
    }

    private RenderablePokemon currentPokemon() {
        List<RenderablePokemon> mons = currentMons();
        if (mons.isEmpty()) return null;
        if (currentIndex < 0 || currentIndex >= mons.size()) currentIndex = 0;
        return mons.get(currentIndex);
    }

    private Component currentName() {
        RenderablePokemon p = currentPokemon();
        return p != null ? p.getSpecies().getTranslatedName() : Component.literal("-");
    }

    /** "Bulbasaur  Grass/Poison" for the panel header. */
    private Component currentHeader() {
        RenderablePokemon p = currentPokemon();
        if (p == null) return Component.literal("-");
        Species sp = p.getSpecies();
        Component types = sp.getPrimaryType().getDisplayName();
        ElementalType t2 = sp.getSecondaryType();
        if (t2 != null) {
            types = Component.empty().append(types).append(Component.literal("/")).append(t2.getDisplayName());
        }
        return Component.empty().append(sp.getTranslatedName())
                .append(Component.literal("  ")).append(types);
    }

    @Override
    protected void init() {
        int totalW = LIST_W + GAP + PANEL_W;
        rootX = (this.width - totalW) / 2;
        if (rootX < 2) rootX = 2;
        rootY = 6;
        panelH = this.height - 12;
        panelX = rootX + LIST_W + GAP;

        if (!hasOptions()) {
            addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                    .bounds(this.width / 2 - 50, this.height / 2 + 40, 100, 20).build());
            return;
        }

        // ---- Left: region list (compact, top-aligned so all regions fit) ----
        int count = categories.size();
        int step = Math.max(12, Math.min(18, panelH / Math.max(1, count)));
        int bh = step - 2;
        int ry = rootY;
        for (int i = 0; i < count; i++) {
            final int idx = i;
            boolean selected = (i == currentCategory);
            Component label = categories.get(i).getDisplayNameText();
            addRenderableWidget(Button.builder(label, b -> selectCategory(idx))
                    .bounds(rootX, ry, LIST_W, bh).build()).active = !selected;
            ry += step;
        }

        // ---- Right: detail panel ----
        int modelX = panelX + 16;
        int modelY = rootY + 16;
        try {
            RenderablePokemon cur = currentPokemon();
            if (cur != null) {
                modelWidget = new ModelWidget(modelX, modelY, 118, 96, cur, 2.0f, 0f, -8.0, true, false, 15);
                addRenderableWidget(modelWidget);
            }
        } catch (Throwable t) {
            modelWidget = null;
        }

        int ay = rootY + 116;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(-1))
                .bounds(panelX + 8, ay, 16, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> cycle(+1))
                .bounds(panelX + PANEL_W - 24, ay, 16, 18).build());
        addRenderableWidget(Button.builder(Component.literal("I choose you!"), b -> addToTeam())
                .bounds(panelX + 28, ay, PANEL_W - 56, 18).build());

        int by = rootY + panelH - 22;
        confirmButton = Button.builder(Component.literal("Confirm your team"), b -> confirm())
                .bounds(rootX, by, totalW, 18).build();
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
        if (size == 0) return;
        currentIndex = (currentIndex + (delta % size) + size) % size;
        refreshModel();
    }

    private void refreshModel() {
        RenderablePokemon cur = currentPokemon();
        if (modelWidget != null && cur != null) {
            try { modelWidget.setPokemon(cur); } catch (Throwable ignored) {}
        }
    }

    private void addToTeam() {
        RenderablePokemon cur = currentPokemon();
        if (cur == null) return;
        Pick pick = new Pick(categories.get(currentCategory).getName(), currentIndex, currentName());
        if (slot1 == null) slot1 = pick;
        else slot2 = pick;   // fill slot2, or replace it so the latest pick wins
        if (confirmButton != null) confirmButton.active = (slot1 != null && slot2 != null);
    }

    private void confirm() {
        if (slot1 == null || slot2 == null) return;
        PacketDistributor.sendToServer(new DualSelectStartersPacket(
                slot1.categoryName(), slot1.index(), slot2.categoryName(), slot2.index()));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xC00A0A12);

        if (!hasOptions()) {
            g.drawCenteredString(this.font, Component.literal("No starters are configured."),
                    this.width / 2, this.height / 2, 0xFFFF5555);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        // Green detail panel.
        drawPanel(g, panelX, rootY, PANEL_W, panelH);

        // Model viewport + platform.
        int modelX = panelX + 16;
        int modelY = rootY + 16;
        g.blit(TEX_BACKGROUND, modelX, modelY, 0, 0, 118, 96, 118, 100);
        g.blit(TEX_PLATFORM, modelX + 2, modelY + 70, 0, 0, 113, 32, 113, 32);
        // (ModelWidget renders itself over the viewport.)

        // Species name + types on a nameplate, below the pick row so Cobblemon's starter
        // toast (top-right) never covers it, and below the 3D model (which draws on top).
        int nameY = rootY + 138;
        g.fill(panelX + 1, nameY - 1, panelX + PANEL_W - 1, nameY + 11, 0xFF0C1E0E);
        g.fill(panelX + 1, nameY - 1, panelX + PANEL_W - 1, nameY, 0xFF3BA55D);
        // Name on the left, Cobblemon type badge(s) on the right of the nameplate.
        g.drawString(this.font, currentName(), panelX + 6, nameY + 2, 0xFFFFFFFF);
        RenderablePokemon cp = currentPokemon();
        if (cp != null) {
            try {
                Species sp = cp.getSpecies();
                new TypeIcon(panelX + PANEL_W - 34, nameY + 1, sp.getPrimaryType(), sp.getSecondaryType(),
                        false, true, 15F, 7.5F, 1F).render(g);
            } catch (Throwable ignored) {}
        }

        // Two team slots, in the gap between the "I choose you!" row and the confirm bar.
        int slotsY = rootY + panelH - 66;
        int sw = (PANEL_W - 22) / 2;
        drawTeamSlot(g, panelX + 6, slotsY, sw, Component.translatable("dualstarters.screen.slot1"), slot1);
        drawTeamSlot(g, panelX + 12 + sw, slotsY, sw, Component.translatable("dualstarters.screen.slot2"), slot2);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF3BA55D);
        g.fill(x, y, x + w, y + h, 0xF0102014);
        g.fill(x, y, x + w, y + 1, 0xFF5BD47E);
    }

    private void drawTeamSlot(GuiGraphics g, int x, int y, int w, Component label, Pick pick) {
        int top = y, bot = y + 34;
        g.fill(x, top, x + w, bot, pick != null ? 0xFF24402A : 0xFF1E3220);
        g.fill(x, top, x + w, top + 1, 0xFF3BA55D);
        g.fill(x, bot - 1, x + w, bot, 0xFF3BA55D);
        g.fill(x, top, x + 1, bot, 0xFF3BA55D);
        g.fill(x + w - 1, top, x + w, bot, 0xFF3BA55D);
        int cx = x + w / 2;
        g.drawCenteredString(this.font, label, cx, top + 3, 0xFFE0FFE6);
        Component val = pick != null ? pick.displayName() : Component.literal("- empty -");
        g.drawCenteredString(this.font, val, cx, top + 16, pick != null ? 0xFFFFFFFF : 0xFFA8A8A8);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
