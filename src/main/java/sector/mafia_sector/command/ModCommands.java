package sector.mafia_sector.command;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import sector.mafia_sector.Mafia_sector;

@EventBusSubscriber(modid = Mafia_sector.MODID)
public final class ModCommands {
    private ModCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        Bunker7Command.register(event.getDispatcher());
        B7Command.register(event.getDispatcher());
    }
}
