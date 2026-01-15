package at.albin.autosell.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class AutoSellController {

    private enum Phase {
        OFF,
        WAITING_FOR_AMOUNT,
        WAITING_FOR_GUI_AFTER_SELL,
        MOVING_ITEMS,
        CLOSING_GUI
    }

    private static boolean enabled = false;

    private static Identifier targetId = null;
    private static Item targetItem = null;
    private static int targetAmount = 0; // 1..64

    private static Phase phase = Phase.OFF;

    // Timing
    private static long notBeforeMoveMs = 0L;
    private static long guiOpenDeadlineMs = 0L;
    private static long nextActionMs = 0L;

    // Slot state
    private static final List<Integer> sourceSlots = new ArrayList<>();
    private static int sourceIndex = 0;

    private static int remainingToTransfer = 0;

    private static boolean holdingFromSource = false;
    private static int heldSourceSlotId = -1;

    private AutoSellController() {}

    public static void start(Identifier id, int amount) {
        MinecraftClient client = MinecraftClient.getInstance();

        amount = clampAmount(amount);

        if (!Registries.ITEM.containsId(id)) {
            msg(client, Text.literal("§c[Autosell] Unbekannte Item-ID: " + id));
            return;
        }

        enabled = true;
        targetId = id;
        targetItem = Registries.ITEM.get(id);
        targetAmount = amount;

        phase = Phase.WAITING_FOR_AMOUNT;
        resetMoveState();

        msg(client, Text.literal("§a[Autosell] Aktiv (startsell): " + id + " x" + amount + " (max 64). Stop: §c/stopsell"));
    }

    public static void stop() {
        MinecraftClient client = MinecraftClient.getInstance();
        enabled = false;
        targetId = null;
        targetItem = null;
        targetAmount = 0;
        phase = Phase.OFF;
        resetMoveState();
        msg(client, Text.literal("§c[Autosell] gestoppt."));
    }

    public static void onClientTick(MinecraftClient client) {
        if (!enabled) return;
        if (client.player == null || client.interactionManager == null) return;
        if (targetItem == null || targetAmount <= 0) return;

        long now = System.currentTimeMillis();

        switch (phase) {
            case WAITING_FOR_AMOUNT -> {
                int count = countInPlayerInventory(client, targetItem);
                if (count < targetAmount) return;

                // für /startsell: nicht im GUI sein, dann /sell senden
                if (isHandledScreenOpen(client)) return;

                ClientPlayNetworkHandler nh = client.player.networkHandler;
                nh.sendChatCommand("sell"); // ohne Slash
                notBeforeMoveMs = now + 2000;
                guiOpenDeadlineMs = now + 10000;
                phase = Phase.WAITING_FOR_GUI_AFTER_SELL;
            }

            case WAITING_FOR_GUI_AFTER_SELL -> {
                if (now < notBeforeMoveMs) return;

                if (!isHandledScreenOpen(client)) {
                    if (now > guiOpenDeadlineMs) {
                        msg(client, Text.literal("§c[Autosell] Sell-GUI nicht geöffnet (Timeout). Versuche später erneut."));
                        phase = Phase.WAITING_FOR_AMOUNT;
                    }
                    return;
                }

                if (!prepareSourceSlots(client)) {
                    msg(client, Text.literal("§c[Autosell] Konnte Source-Slots nicht finden. Stoppe."));
                    stop();
                    return;
                }

                remainingToTransfer = targetAmount;
                nextActionMs = now + 250;
                phase = Phase.MOVING_ITEMS;
            }

            case MOVING_ITEMS -> {
                if (!isHandledScreenOpen(client)) {
                    // GUI wurde geschlossen
                    resetMoveState();
                    phase = Phase.WAITING_FOR_AMOUNT;
                    return;
                }
                if (now < nextActionMs) return;

                boolean done = doOneMoveStep(client);
                if (done) {
                    nextActionMs = now + 350;
                    phase = Phase.CLOSING_GUI;
                }
            }

            case CLOSING_GUI -> {
                if (now < nextActionMs) return;
                client.player.closeHandledScreen();
                resetMoveState();
                phase = Phase.WAITING_FOR_AMOUNT;
            }

            default -> { }
        }
    }

    private static int clampAmount(int amount) {
        if (amount < 1) return 1;
        return Math.min(amount, 64);
    }

    private static void resetMoveState() {
        remainingToTransfer = 0;
        sourceSlots.clear();
        sourceIndex = 0;
        holdingFromSource = false;
        heldSourceSlotId = -1;
        notBeforeMoveMs = 0L;
        guiOpenDeadlineMs = 0L;
        nextActionMs = 0L;
    }

    private static boolean isHandledScreenOpen(MinecraftClient client) {
        return client.player.currentScreenHandler != null
                && client.player.playerScreenHandler != null
                && client.player.currentScreenHandler != client.player.playerScreenHandler;
    }

    private static boolean prepareSourceSlots(MinecraftClient client) {
        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null) return false;

        Inventory playerInv = client.player.getInventory();

        sourceSlots.clear();
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot s = handler.getSlot(i);
            if (s.inventory == playerInv) {
                sourceSlots.add(i);
            }
        }

        sourceIndex = 0;
        holdingFromSource = false;
        heldSourceSlotId = -1;
        return !sourceSlots.isEmpty();
    }

    /**
     * Ein kleiner Schritt pro Tick/Delay.
     * Mit amount<=64 ist es:
     * - Stack aufnehmen
     * - falls remaining==64 und Cursor==64: linksklick in Zielslot (1 action)
     * - sonst rechtsklick 1x pro Item in Zielslot (langsamer, aber exakt)
     * - restlichen Cursor zurück in Source
     */
    private static boolean doOneMoveStep(MinecraftClient client) {
        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null) return true;

        // fertig: Cursor zurücklegen, wenn noch was gehalten wird
        if (remainingToTransfer <= 0) {
            ItemStack cursor = handler.getCursorStack();
            if (!cursor.isEmpty() && holdingFromSource && heldSourceSlotId >= 0) {
                click(client, handler, heldSourceSlotId, 0, SlotActionType.PICKUP);
                holdingFromSource = false;
                heldSourceSlotId = -1;
                nextActionMs = System.currentTimeMillis() + 250;
                return false;
            }
            return true;
        }

        // Wenn nichts in der Hand: nimm einen Stack aus Inventory
        if (handler.getCursorStack().isEmpty()) {
            int src = findNextSourceSlotWithItem(handler);
            if (src == -1) {
                msg(client, Text.literal("§c[Autosell] Nicht genug Items im Inventar (während Transfer)."));
                return true;
            }
            click(client, handler, src, 0, SlotActionType.PICKUP);
            holdingFromSource = true;
            heldSourceSlotId = src;
            nextActionMs = System.currentTimeMillis() + 300;
            return false;
        }

        // Zielslot finden (nicht player inventory)
        int dst = findDestinationSlot(handler, client.player.getInventory());
        if (dst == -1) {
            msg(client, Text.literal("§c[Autosell] Kein Platz im Sell-GUI."));
            remainingToTransfer = 0; // damit wir zurücklegen
            nextActionMs = System.currentTimeMillis() + 200;
            return false;
        }

        ItemStack cursor = handler.getCursorStack();
        if (cursor.isEmpty() || !cursor.isOf(targetItem)) {
            // irgendwas schief gelaufen -> abbrechen (und ggf. zurücklegen)
            msg(client, Text.literal("§c[Autosell] Cursor enthält nicht das Ziel-Item. Runde abgebrochen."));
            remainingToTransfer = 0;
            nextActionMs = System.currentTimeMillis() + 200;
            return false;
        }

        // Wenn remaining == 64 und cursor hat 64: kompletten Stack rein (linksklick)
        if (remainingToTransfer == 64 && cursor.getCount() >= 64) {
            click(client, handler, dst, 0, SlotActionType.PICKUP);
            remainingToTransfer = 0;
            nextActionMs = System.currentTimeMillis() + 450;
            return false;
        }

        // Sonst: itemweise rein (rechtsklick = button 1)
        click(client, handler, dst, 1, SlotActionType.PICKUP);
        remainingToTransfer -= 1;
        nextActionMs = System.currentTimeMillis() + 120;
        return false;
    }

    private static int findNextSourceSlotWithItem(ScreenHandler handler) {
        // Vorwärts
        for (int i = sourceIndex; i < sourceSlots.size(); i++) {
            int slotId = sourceSlots.get(i);
            ItemStack stack = handler.getSlot(slotId).getStack();
            if (!stack.isEmpty() && stack.isOf(targetItem)) {
                sourceIndex = i + 1;
                return slotId;
            }
        }
        // Wrap-around
        for (int i = 0; i < sourceSlots.size(); i++) {
            int slotId = sourceSlots.get(i);
            ItemStack stack = handler.getSlot(slotId).getStack();
            if (!stack.isEmpty() && stack.isOf(targetItem)) {
                sourceIndex = i + 1;
                return slotId;
            }
        }
        return -1;
    }

    private static int findDestinationSlot(ScreenHandler handler, Inventory playerInv) {
        ItemStack probe = new ItemStack(targetItem);

        for (int i = 0; i < handler.slots.size(); i++) {
            Slot s = handler.getSlot(i);
            if (s.inventory == playerInv) continue; // nur GUI-Slots

            ItemStack inSlot = s.getStack();
            boolean empty = inSlot.isEmpty();
            boolean same = !empty && ItemStack.areItemsEqual(inSlot, probe);
            boolean hasRoom = empty || (same && inSlot.getCount() < inSlot.getMaxCount());

            if (hasRoom && s.canInsert(probe) && handler.canInsertIntoSlot(probe, s)) {
                return i;
            }
        }
        return -1;
    }

    private static int countInPlayerInventory(MinecraftClient client, Item item) {
        var inv = client.player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.isOf(item)) total += stack.getCount();
        }
        return total;
    }

    private static void click(MinecraftClient client, ScreenHandler handler, int slotId, int button, SlotActionType type) {
        client.interactionManager.clickSlot(handler.syncId, slotId, button, type, client.player);
    }

    private static void msg(MinecraftClient client, Text text) {
        if (client.player != null) client.player.sendMessage(text, false);
    }
}
