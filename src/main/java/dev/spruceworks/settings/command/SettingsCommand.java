package dev.spruceworks.settings.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.spruceworks.settings.SpruceSettingsPlugin;
import dev.spruceworks.settings.gui.SettingsGui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /settings} — opens the per-player toggle GUI. */
public final class SettingsCommand {

    private final SpruceSettingsPlugin plugin;

    private SettingsCommand(SpruceSettingsPlugin plugin) {
        this.plugin = plugin;
    }

    public static void register(SpruceSettingsPlugin plugin) {
        SettingsCommand command = new SettingsCommand(plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build(), "SpruceSettings player settings",
                        java.util.List.of("prefs", "options")));
    }

    private LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("settings")
                .requires(source -> source.getSender().hasPermission("sprucesettings.use"))
                .executes(this::open)
                .build();
    }

    private int open(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "players-only");
            return Command.SINGLE_SUCCESS;
        }
        new SettingsGui(this.plugin, player).open();
        return Command.SINGLE_SUCCESS;
    }
}
