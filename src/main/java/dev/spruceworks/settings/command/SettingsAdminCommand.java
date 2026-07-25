package dev.spruceworks.settings.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.spruceworks.settings.SpruceSettingsPlugin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

/** {@code /settingsadmin reload|reset <player>}. */
public final class SettingsAdminCommand {

    private final SpruceSettingsPlugin plugin;

    private SettingsAdminCommand(SpruceSettingsPlugin plugin) {
        this.plugin = plugin;
    }

    public static void register(SpruceSettingsPlugin plugin) {
        SettingsAdminCommand command = new SettingsAdminCommand(plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build(), "SpruceSettings admin commands"));
    }

    private LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("settingsadmin")
                .requires(source -> source.getSender().hasPermission("sprucesettings.admin"))
                .executes(this::usage)
                .then(Commands.literal("reload").executes(this::reload))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
                                    return builder.buildFuture();
                                })
                                .executes(this::reset)))
                .build();
    }

    private int usage(CommandContext<CommandSourceStack> context) {
        this.plugin.messages().send(context.getSource().getSender(), "usage-settingsadmin");
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        boolean ok = this.plugin.reloadPluginConfig();
        this.plugin.messages().send(sender, ok ? "reload-success" : "reload-failed");
        return Command.SINGLE_SUCCESS;
    }

    private int reset(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String name = StringArgumentType.getString(context, "player");
        var online = Bukkit.getPlayerExact(name);
        OfflinePlayer target = online != null ? online : null;
        if (target == null) {
            // Deliberately online-only: resolving an arbitrary name offline means
            // a potentially blocking Mojang lookup, which is never worth it for
            // an admin convenience command.
            this.plugin.messages().send(sender, "player-not-found", Placeholder.unparsed("target", name));
            return Command.SINGLE_SUCCESS;
        }
        this.plugin.settingsService().clear(target.getUniqueId());
        if (online != null) {
            this.plugin.visibilityService().applyForViewer(online);
        }
        this.plugin.messages().send(sender, "reset-success", Placeholder.unparsed("target", name));
        return Command.SINGLE_SUCCESS;
    }
}
