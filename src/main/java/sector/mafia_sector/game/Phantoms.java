package sector.mafia_sector.game;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.card.CardType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Debug stand-ins, so one admin can play a whole game: armor stands with the participant's UUID (the client shows
 * their cards like a player's), revealing and voting at random.
 */
final class Phantoms {
    private Phantoms() {
    }

    static void spawn(ServerLevel level, GameSession game) {
        for (GameSession.Participant participant : game.participants()) {
            if (!participant.isPhantom()) continue;
            ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
            stand.setUUID(participant.uuid());
            Vec3 pos = participant.seat().spawn();
            stand.moveTo(pos.x, pos.y, pos.z, participant.seat().yaw(), 0.0F);
            stand.setCustomName(Component.literal(participant.name()));
            stand.setCustomNameVisible(true);
            stand.setInvulnerable(true);
            stand.setNoGravity(true);
            stand.setShowArms(true);
            level.addFreshEntity(stand);
        }
    }

    static void remove(ServerLevel level, GameSession game) {
        for (GameSession.Participant participant : game.participants()) {
            if (participant.isPhantom()) {
                Entity entity = level.getEntity(participant.uuid());
                if (entity != null) entity.discard();
            }
        }
    }

    static void moveToSurface(ServerLevel level, GameSession.Participant participant) {
        if (participant.isPhantom() && level.getEntity(participant.uuid()) instanceof ArmorStand stand) {
            Vec3 pos = BunkerLayout.SURFACE_SPAWN.add(participant.place() % 4 * 2 - 3, 0, participant.place() / 4 - 2);
            stand.teleportTo(pos.x, pos.y, pos.z);
        }
    }

    /** On a phantom's turn: reveal a random card, then pass the turn on. */
    static void onTurnTick(MinecraftServer server, GameSession game) {
        if (game.currentTurn() == null || !(game.get(game.currentTurn()) instanceof GameSession.Participant participant) || !participant.isPhantom()) {
            return;
        }
        if (game.phaseTicks() >= 30 && !game.revealedThisTurn()) {
            List<CardType> hidden = participant.dossier().hiddenRevealable();
            if (!hidden.isEmpty()) {
                RoundController.reveal(server, game, participant.uuid(), hidden.get(ThreadLocalRandom.current().nextInt(hidden.size())));
            }
        }
        if (game.phaseTicks() >= 60 && game.revealedThisTurn()) {
            RoundController.endTurn(server, game, participant.uuid());
        }
    }

    /** Each phantom votes for a random candidate a moment into the vote. */
    static void onVoteTick(MinecraftServer server, GameSession game) {
        for (GameSession.Participant participant : game.alive()) {
            if (!participant.isPhantom() || game.hasVoted(participant.uuid()) || game.phaseTicks() < 20 + participant.place() * 2) {
                continue;
            }
            List<GameSession.Participant> targets = game.alive().stream()
                    .filter(p -> p != participant && game.isCandidate(p.uuid())).toList();
            if (!targets.isEmpty()) {
                VoteManager.cast(server, game, participant.uuid(), targets.get(ThreadLocalRandom.current().nextInt(targets.size())));
            }
        }
    }
}
