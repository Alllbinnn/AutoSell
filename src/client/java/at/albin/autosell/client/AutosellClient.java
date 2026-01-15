package at.albin.autosell.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.util.Identifier;

public class AutosellClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        ClientTickEvents.END_CLIENT_TICK.register(AutoSellController::onClientTick);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            dispatcher.register(
                    ClientCommandManager.literal("startsell")
                            .then(ClientCommandManager.argument("id", IdentifierArgumentType.identifier())
                                    .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(1, 64))
                                            .executes(ctx -> {
                                                Identifier id = ctx.getArgument("id", Identifier.class);
                                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                AutoSellController.start(id, amount);
                                                return 1;
                                            })
                                    )
                            )
            );

            dispatcher.register(
                    ClientCommandManager.literal("stopsell")
                            .executes(ctx -> {
                                AutoSellController.stop();
                                return 1;
                            })
            );
        });
    }
}
