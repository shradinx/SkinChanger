package me.edoren.skin_changer.server;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.edoren.skin_changer.common.SharedPool;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("SameReturnValue")
public class SkinsCommand {
    static final Component ISSUER = Component.literal("SkinChanger");
    
    public static ArgumentBuilder<CommandSourceStack, ?> setCommand(Function3<CommandSourceStack, Player, String, Integer> setFunction,
                                                                    Function<CommandContext<CommandSourceStack>, Player> getTarget) {
        return Commands.literal("set").then(Commands.argument("arg", MessageArgument.message()).executes((context) ->
            setFunction.apply(context.getSource(), getTarget.apply(context), MessageArgument.getMessage(context, "arg").getString())
        ));
    }
    
    public static ArgumentBuilder<CommandSourceStack, ?> clearCommand(Function2<CommandSourceStack, Player, Integer> clearFunction,
                                                                      Function<CommandContext<CommandSourceStack>, Player> getTarget) {
        return Commands.literal("clear").executes((context) ->
            clearFunction.apply(context.getSource(), getTarget.apply(context))
        );
    }
    
    public static ArgumentBuilder<CommandSourceStack, ?> setAllCommand(Function2<CommandSourceStack, String[], Integer> setAllFunction) {
        return Commands.literal("setall").then(Commands.argument("arg", StringArgumentType.greedyString()).executes((context) ->
            setAllFunction.apply(context.getSource(), StringArgumentType.getString(context, "arg").split("\\s+"))
        ));
    }
    
    public static ArgumentBuilder<CommandSourceStack, ?> clearAllCommand(Function<CommandSourceStack, Integer> clearAllFunction) {
        return Commands.literal("clearall").executes((context) ->
            clearAllFunction.apply(context.getSource())
        );
    }
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("skin")
            .requires(p -> p.hasPermission(2))
            .then(setCommand(
                SkinsCommand::setPlayerSkin,
                (context) -> context.getSource().getPlayerOrException())
            )
            .then(setAllCommand(SkinsCommand::setAllPlayerSkin))
            .then(clearCommand(
                SkinsCommand::clearPlayerSkin,
                (context) -> context.getSource().getPlayerOrException())
            )
            .then(clearAllCommand(SkinsCommand::clearAllPlayerSkin))
            .then(Commands.literal("player")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(setCommand(
                        SkinsCommand::setPlayerSkin,
                        (context) -> EntityArgument.getPlayer(context, "target"))
                    )
                    .then(clearCommand(
                        SkinsCommand::clearPlayerSkin,
                        (context) -> EntityArgument.getPlayer(context, "target"))
                    )
                )
            )
        );
        dispatcher.register(Commands.literal("cape")
            .requires(p -> p.hasPermission(2))
            .then(setCommand(
                SkinsCommand::setPlayerCape,
                (context) -> context.getSource().getPlayerOrException())
            )
            .then(setAllCommand(SkinsCommand::setAllPlayerCape))
            .then(clearCommand(
                SkinsCommand::clearPlayerCape,
                (context) -> context.getSource().getPlayerOrException())
            )
            .then(clearAllCommand(SkinsCommand::clearAllPlayerCape))
            .then(Commands.literal("player")
                .requires((player) -> player.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player())
                    .then(setCommand(
                        SkinsCommand::setPlayerCape,
                        (context) -> EntityArgument.getPlayer(context, "target"))
                    )
                    .then(clearCommand(
                        SkinsCommand::clearPlayerCape,
                        (context) -> EntityArgument.getPlayer(context, "target"))
                    )
                )
            )
        );
    }
    
    private static Integer setAllPlayerSkin(CommandSourceStack source, String[] args) {
        List<String> skins = Arrays.stream(args).toList();
        List<List<Player>> sectionedPlayers = partitionPlayers(source, skins);
        for (int i = 0; i < Math.min(skins.size(), sectionedPlayers.size()); i++) {
            String skin = skins.get(i);
            List<Player> players = sectionedPlayers.get(i);
            for (Player p : players) {
                setPlayerSkin(source, p, skin);
            }
        }
        return 1;
    }
    
    private static Integer setPlayerSkin(CommandSourceStack source, Entity targetEntity, String arg) {
        CommandSource sourcePlayer = source.getServer();
        if (source.getEntity() != null) {
            sourcePlayer = source.getEntity();
        }
        Player targetPlayer = (Player) targetEntity;
        try {
            URL url = URI.create(arg).toURL();
            Level l = targetPlayer.level();
            if (l.getServer() != null)
                return setPlayerSkinByURL(sourcePlayer, targetPlayer, url);
        } catch (MalformedURLException | IllegalArgumentException e) {
            return setPlayerSkinByName(sourcePlayer, targetPlayer, arg);
        }
        return 1;
    }
    
    private static Integer setPlayerSkinByName(CommandSource sourcePlayer, Player targetPlayer, String playerName) {
        sourcePlayer.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER, Component.translatable("commands.skin_changer.skin.loading")));
        SharedPool.get().execute(() -> {
            if (!SkinProviderController.GetInstance().setPlayerSkinByName(targetPlayer.getGameProfile(), playerName, true)) {
                sourcePlayer.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER,
                    Component.translatable("commands.skin_changer.skin.load_target_failed", playerName)));
            } else {
                sourcePlayer.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER,
                    Component.translatable("commands.skin_changer.skin.load_target_succeeded", playerName, targetPlayer.getName().getString())));
            }
        });
        return 1;
    }
    
    private static Integer setPlayerSkinByURL(CommandSource sourcePlayer, Player targetPlayer, URL url) {
        sourcePlayer.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER, Component.translatable("commands.skin_changer.skin.loading")));
        SharedPool.get().execute(() -> {
            if (!SkinProviderController.GetInstance().setPlayerSkinByURL(targetPlayer.getGameProfile(), url, true)) {
                sourcePlayer.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER,
                    Component.translatable("commands.skin_changer.skin.load_target_failed", url.toString())));
            } else {
                sourcePlayer.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER,
                    Component.translatable("commands.skin_changer.skin.load_target_succeeded", url.toString(), targetPlayer.getName().getString())));
            }
        });
        return 1;
    }
    
    private static Integer setAllPlayerCape(CommandSourceStack source, String[] args) {
        List<String> skins = Arrays.stream(args).toList();
        List<List<Player>> sectionedPlayers = partitionPlayers(source, skins);
        for (int i = 0; i < Math.min(skins.size(), sectionedPlayers.size()); i++) {
            String cape = skins.get(i);
            List<Player> players = sectionedPlayers.get(i);
            for (Player p : players) {
                setPlayerCape(source, p, cape);
            }
        }
        return 1;
    }
    
    private static Integer setPlayerCape(CommandSourceStack source, Entity targetEntity, String arg) {
        CommandSource sourcePlayer = source.getServer();
        if (source.getEntity() != null) {
            sourcePlayer = source.getEntity();
        }
        Player targetPlayer = (Player) targetEntity;
        try {
            URL url = URI.create(arg).toURL();
            Level l = targetPlayer.level();
            if (l.getServer() != null)
                return setPlayerCapeByURL(sourcePlayer, targetPlayer, url);
        } catch (MalformedURLException | IllegalArgumentException e) {
            return setPlayerCapeByName(sourcePlayer, targetPlayer, arg);
        }
        return 1;
    }
    
    private static Integer setPlayerCapeByName(CommandSource sourcePlayer, Player targetPlayer, String playerName) {
        sourcePlayer.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER, Component.translatable("commands.skin_changer.cape.loading")));
        SharedPool.get().execute(() -> {
            if (!SkinProviderController.GetInstance().setPlayerCapeByName(targetPlayer.getGameProfile(), playerName, true)) {
                sourcePlayer.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER,
                    Component.translatable("commands.skin_changer.cape.load_target_failed", playerName)));
            } else {
                sourcePlayer.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER,
                    Component.translatable("commands.skin_changer.cape.load_target_succeeded", playerName, targetPlayer.getName().getString())));
            }
        });
        return 1;
    }
    
    private static Integer setPlayerCapeByURL(CommandSource sourcePlayer, Player targetPlayer, URL url) {
        sourcePlayer.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER, Component.translatable("commands.skin_changer.cape.loading")));
        SharedPool.get().execute(() -> {
            if (!SkinProviderController.GetInstance().setPlayerCapeByURL(targetPlayer.getGameProfile(), url, true)) {
                sourcePlayer.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER,
                    Component.translatable("commands.skin_changer.cape.load_target_failed", url.toString())));
            } else {
                sourcePlayer.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER,
                    Component.translatable("commands.skin_changer.cape.load_target_succeeded", url.toString(), targetPlayer.getName().getString())));
            }
        });
        return 1;
    }
    
    private static Integer clearAllPlayerSkin(CommandSourceStack source) throws CommandSyntaxException {
        Level level = source.getLevel();
        for (Player target : level.players()) {
            clearPlayerSkin(source, target);
        }
        return 1;
    }
    
    private static Integer clearPlayerSkin(CommandSourceStack source, Entity targetEntity) throws CommandSyntaxException {
        Player sourcePlayer = (Player) source.getEntityOrException();
        Player targetPlayer = (Player) targetEntity;
        sourcePlayer.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER, Component.translatable("commands.skin_changer.skin.removing")));
        SharedPool.get().execute(() -> {
            SkinProviderController.GetInstance().clearPlayerSkin(targetPlayer.getGameProfile());
            sourcePlayer.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER,
                Component.translatable("commands.skin_changer.skin.remove_target_succeeded", targetEntity.getName().getString())));
        });
        return 1;
    }
    
    private static Integer clearAllPlayerCape(CommandSourceStack source) throws CommandSyntaxException {
        Level level = source.getLevel();
        for (Player target : level.players()) {
            clearPlayerCape(source, target);
        }
        return 1;
    }
    
    private static Integer clearPlayerCape(CommandSourceStack source, Entity targetEntity) throws CommandSyntaxException {
        Player sourcePlayer = (Player) source.getEntityOrException();
        Player targetPlayer = (Player) targetEntity;
        sourcePlayer.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER, Component.translatable("commands.skin_changer.cape.removing")));
        SharedPool.get().execute(() -> {
            SkinProviderController.GetInstance().clearPlayerCape(targetPlayer.getGameProfile());
            sourcePlayer.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER,
                Component.translatable("commands.skin_changer.cape.remove_target_succeeded", targetEntity.getName().getString())));
        });
        return 1;
    }
    
    private static List<List<Player>> partitionPlayers(CommandSourceStack source, List<String> skins) {
        Level level = source.getLevel();
        if (skins.isEmpty()) {
            source.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER,
                Component.translatable("commands.skin_changer.skin.provide_skin")));
            return List.of();
        }
        
        List<Player> allPlayers = level.players().stream()
            .map(p -> level.getPlayerByUUID(p.getUUID()))
            .toList();
        
        if (allPlayers.isEmpty()) {
            source.sendSystemMessage(Component.translatable("chat.type.announcement", ISSUER,
                Component.translatable("commands.skin_changer.global.no_players")));
            return List.of();
        }
        
        int groupSize = Math.max((int) Math.ceil(((double) allPlayers.size()) / ((double) skins.size())), 1);
        
        return Lists.partition(
            new ArrayList<>(allPlayers), groupSize
        );
    }
    
    @FunctionalInterface
    public interface Function<T, R> {
        R apply(T t) throws CommandSyntaxException;
    }
    
    @FunctionalInterface
    public interface Function2<T, U, R> {
        R apply(T t, U u) throws CommandSyntaxException;
    }
    
    @FunctionalInterface
    public interface Function3<T, U, V, R> {
        R apply(T t, U u, V v) throws CommandSyntaxException;
    }
}