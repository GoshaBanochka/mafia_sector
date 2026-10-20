package sector.mafia_sector.game;

import org.jetbrains.annotations.Nullable;
import sector.mafia_sector.build.BunkerLayout;
import sector.mafia_sector.card.BunkerInfo;
import sector.mafia_sector.card.Catastrophe;
import sector.mafia_sector.card.Deck;
import sector.mafia_sector.card.Dossier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A running game. Lives only in memory: a server restart cancels the game and the stash returns everyone's things. */
public final class GameSession {
    private final Map<UUID, Participant> participants = new LinkedHashMap<>();
    private final boolean debug;
    private final GameHud hud = new GameHud();
    private final Catastrophe catastrophe;
    private final BunkerInfo bunker;
    private final Deck deck;
    private final int seats;
    /** Exiles left per round, index 0 = round 1. Leavers and repeated ties move entries around. */
    private final List<Integer> plan;

    private GamePhase phase = GamePhase.INTRO;
    private int round = 1;
    private int phaseTicksLeft;
    private int phaseTicksTotal;
    /** Ticks since the phase began, pauses excluded. Drives phantoms and short delays. */
    private int phaseTicks;
    private boolean paused;
    /** Game time played so far, pauses excluded. */
    private long elapsedTicks;
    private int exiledCount;
    /** Set when anything players can see has changed; the sync sends it out. */
    private boolean dirty = true;

    // Turns of the reveal phase.
    private final Deque<UUID> turnQueue = new ArrayDeque<>();
    private final Set<UUID> missedTurns = new LinkedHashSet<>();
    @Nullable
    private UUID currentTurn;
    private boolean revealedThisTurn;

    // Votes. Modifiers from special actions last for the round.
    final Map<UUID, UUID> votes = new LinkedHashMap<>();
    final Set<UUID> candidates = new LinkedHashSet<>();
    final Set<UUID> doubleVote = new HashSet<>();
    final Set<UUID> immune = new HashSet<>();
    final Set<UUID> protectedThisRound = new HashSet<>();
    final Set<UUID> gagged = new HashSet<>();
    int votesLeftThisRound;

    // Final trial.
    int reactorHoldTicks;
    @Nullable
    Boolean trialWon;
    float reactorSpeed;
    float reactorCapacity;
    float reactorNeeded;

    GameSession(boolean debug, Catastrophe catastrophe, BunkerInfo bunker, Deck deck, int seats, List<Integer> plan) {
        this.debug = debug;
        this.catastrophe = catastrophe;
        this.bunker = bunker;
        this.deck = deck;
        this.seats = seats;
        this.plan = new ArrayList<>(plan);
    }

    public static final class Participant {
        private final UUID uuid;
        private final String name;
        /** Index into {@link BunkerLayout#SEATS} and {@link BunkerLayout#VOTE_CHESTS}. */
        private final int place;
        private final boolean phantom;
        private final Dossier dossier = new Dossier();
        private boolean online = true;
        private long offlineSinceTick = -1;
        private boolean alive = true;
        /** 1 for the first exiled, 2 for the second... 0 while in the bunker. */
        private int exileOrder;
        private int exileRound;

        Participant(UUID uuid, String name, int place, boolean phantom) {
            this.uuid = uuid;
            this.name = name;
            this.place = place;
            this.phantom = phantom;
        }

        public UUID uuid() {
            return uuid;
        }

        public String name() {
            return name;
        }

        public int place() {
            return place;
        }

        /** A debug stand-in without a player: reveals and votes at random. */
        public boolean isPhantom() {
            return phantom;
        }

        public Dossier dossier() {
            return dossier;
        }

        /** The own standing place in the council circle. */
        public BunkerLayout.Seat seat() {
            return BunkerLayout.SEATS.get(place);
        }

        /** The chest with this player's name in the voting room. */
        public BunkerLayout.VoteChest voteChest() {
            return BunkerLayout.VOTE_CHESTS.get(place);
        }

        public boolean isOnline() {
            return phantom || online;
        }

        public long offlineSinceTick() {
            return offlineSinceTick;
        }

        void setOnline(boolean online, long tick) {
            this.online = online;
            this.offlineSinceTick = online ? -1 : tick;
        }

        /** Still in the bunker, i.e. not exiled. */
        public boolean isAlive() {
            return alive;
        }

        public int exileOrder() {
            return exileOrder;
        }

        public int exileRound() {
            return exileRound;
        }

        void exile(int order, int round) {
            this.alive = false;
            this.exileOrder = order;
            this.exileRound = round;
        }
    }

    void add(Participant participant) {
        participants.put(participant.uuid(), participant);
    }

    @Nullable
    public Participant get(UUID uuid) {
        return participants.get(uuid);
    }

    public boolean has(UUID uuid) {
        return participants.containsKey(uuid);
    }

    public Collection<Participant> participants() {
        return Collections.unmodifiableCollection(participants.values());
    }

    public List<Participant> alive() {
        return participants.values().stream().filter(Participant::isAlive).toList();
    }

    @Nullable
    public Participant byName(String name) {
        return participants.values().stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public boolean isDebug() {
        return debug;
    }

    public GameHud hud() {
        return hud;
    }

    public Catastrophe catastrophe() {
        return catastrophe;
    }

    public BunkerInfo bunker() {
        return bunker;
    }

    public Deck deck() {
        return deck;
    }

    /** Places in the bunker: the game ends when this many are left. */
    public int seats() {
        return seats;
    }

    public List<Integer> plan() {
        return Collections.unmodifiableList(plan);
    }

    public int totalRounds() {
        return plan.size();
    }

    /** Exiles planned for the given round (1-based). */
    public int exilesInRound(int round) {
        return round >= 1 && round <= plan.size() ? plan.get(round - 1) : 0;
    }

    void setExilesInRound(int round, int count) {
        while (plan.size() < round) {
            plan.add(0);
        }
        plan.set(round - 1, Math.max(0, count));
    }

    public GamePhase phase() {
        return phase;
    }

    public int round() {
        return round;
    }

    void setRound(int round) {
        this.round = round;
    }

    public int phaseTicksLeft() {
        return phaseTicksLeft;
    }

    public int phaseTicksTotal() {
        return phaseTicksTotal;
    }

    public int phaseTicks() {
        return phaseTicks;
    }

    public boolean isPaused() {
        return paused;
    }

    void setPaused(boolean paused) {
        this.paused = paused;
        dirty = true;
    }

    public long elapsedTicks() {
        return elapsedTicks;
    }

    int nextExileOrder() {
        return ++exiledCount;
    }

    void enterPhase(GamePhase phase, int ticks) {
        this.phase = phase;
        this.phaseTicksTotal = Math.max(1, ticks);
        this.phaseTicksLeft = phaseTicksTotal;
        this.phaseTicks = 0;
        dirty = true;
    }

    /** Restarts the clock of the current phase, e.g. for the next turn. */
    void restartClock(int ticks) {
        enterPhase(phase, ticks);
    }

    /** Cuts the time left in the current phase down to {@code ticks}, keeping the phase. */
    void restartClockKeepingPhase(int ticks) {
        phaseTicksLeft = Math.min(phaseTicksLeft, ticks);
        dirty = true;
    }

    /** Advances the clock by one tick; returns true when the phase has run out. */
    boolean tickClock() {
        if (paused) {
            return false;
        }
        elapsedTicks++;
        phaseTicks++;
        return --phaseTicksLeft <= 0;
    }

    // ------------------------------------------------------------------ turns

    Deque<UUID> turnQueue() {
        return turnQueue;
    }

    Set<UUID> missedTurns() {
        return missedTurns;
    }

    @Nullable
    public UUID currentTurn() {
        return currentTurn;
    }

    void setCurrentTurn(@Nullable UUID uuid) {
        this.currentTurn = uuid;
        this.revealedThisTurn = false;
        dirty = true;
    }

    public boolean revealedThisTurn() {
        return revealedThisTurn;
    }

    void setRevealedThisTurn() {
        revealedThisTurn = true;
        dirty = true;
    }

    // ------------------------------------------------------------------ votes

    public boolean hasVoted(UUID voter) {
        return votes.containsKey(voter);
    }

    /** The players one may vote against now: everyone in the bunker, or the tied ones at a revote. */
    public boolean isCandidate(UUID uuid) {
        Participant p = participants.get(uuid);
        return p != null && p.isAlive() && (candidates.isEmpty() || candidates.contains(uuid));
    }

    public Set<UUID> candidates() {
        return Collections.unmodifiableSet(candidates);
    }

    void newRoundModifiers() {
        doubleVote.clear();
        immune.clear();
        protectedThisRound.clear();
        gagged.clear();
    }

    // ------------------------------------------------------------------ trial

    @Nullable
    public Boolean trialWon() {
        return trialWon;
    }

    public float reactorSpeed() {
        return reactorSpeed;
    }

    public float reactorCapacity() {
        return reactorCapacity;
    }

    public float reactorNeeded() {
        return reactorNeeded;
    }

    public int reactorHoldTicks() {
        return reactorHoldTicks;
    }

    // ------------------------------------------------------------------ sync

    void markDirty() {
        dirty = true;
    }

    boolean takeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }
}
