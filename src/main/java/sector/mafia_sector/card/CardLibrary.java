package sector.mafia_sector.card;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import sector.mafia_sector.Mafia_sector;
import sector.mafia_sector.action.SpecialAction;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Cards, catastrophes and bunker descriptions from data packs, namespace bunker7:
 * cards/&lt;type&gt;.json, catastrophes.json, bunkers.json. Reloaded with /reload, so admins can change them without
 * rebuilding the mod. A data pack replaces a whole file (the top pack wins).
 */
@EventBusSubscriber(modid = Mafia_sector.MODID)
public final class CardLibrary {
    public static final String NAMESPACE = "bunker7";

    private static volatile Contents contents = new Contents(new EnumMap<>(CardType.class), List.of(), List.of(), List.of("not loaded"));

    public record Contents(Map<CardType, List<Card>> cards, List<Catastrophe> catastrophes, List<BunkerInfo> bunkers, List<String> errors) {
        public List<Card> cards(CardType type) {
            return cards.getOrDefault(type, List.of());
        }
    }

    private CardLibrary() {
    }

    public static Contents get() {
        return contents;
    }

    @SubscribeEvent
    static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    private static final class Loader extends SimplePreparableReloadListener<Contents> {
        @Override
        protected Contents prepare(ResourceManager manager, ProfilerFiller profiler) {
            return load(manager);
        }

        @Override
        protected void apply(Contents loaded, ResourceManager manager, ProfilerFiller profiler) {
            contents = loaded;
            loaded.errors().forEach(error -> Mafia_sector.LOGGER.error("Bunker 7 cards: {}", error));
            Mafia_sector.LOGGER.info("Bunker 7: loaded {} cards, {} catastrophes, {} bunkers",
                    loaded.cards().values().stream().mapToInt(List::size).sum(), loaded.catastrophes().size(), loaded.bunkers().size());
        }
    }

    static Contents load(ResourceManager manager) {
        List<String> errors = new ArrayList<>();
        Map<CardType, List<Card>> cards = new EnumMap<>(CardType.class);
        for (CardType type : CardType.values()) {
            List<Card> list = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            read(manager, "cards/" + type.id() + ".json", errors).ifPresent(root -> {
                for (JsonElement element : GsonHelper.getAsJsonArray(root, "cards", new JsonArray())) {
                    try {
                        JsonObject o = element.getAsJsonObject();
                        String id = GsonHelper.getAsString(o, "id");
                        SpecialAction action = null;
                        if (type == CardType.ACTION) {
                            String actionId = GsonHelper.getAsString(o, "action");
                            action = SpecialAction.byId(actionId);
                            if (action == null) {
                                errors.add(type.id() + "/" + id + ": unknown action '" + actionId + "'");
                                continue;
                            }
                        }
                        if (!ids.add(id)) {
                            errors.add(type.id() + ": duplicate id '" + id + "'");
                            continue;
                        }
                        list.add(new Card(id, type, GsonHelper.getAsString(o, "text"), GsonHelper.getAsString(o, "detail", ""), action));
                    } catch (RuntimeException e) {
                        errors.add(type.id() + ": bad card " + element + ": " + e.getMessage());
                    }
                }
            });
            cards.put(type, List.copyOf(list));
        }
        List<Catastrophe> catastrophes = new ArrayList<>();
        read(manager, "catastrophes.json", errors).ifPresent(root -> {
            for (JsonElement element : GsonHelper.getAsJsonArray(root, "catastrophes", new JsonArray())) {
                try {
                    JsonObject o = element.getAsJsonObject();
                    catastrophes.add(new Catastrophe(GsonHelper.getAsString(o, "id"), GsonHelper.getAsString(o, "title"),
                            GsonHelper.getAsString(o, "description")));
                } catch (RuntimeException e) {
                    errors.add("catastrophes: bad entry " + element + ": " + e.getMessage());
                }
            }
        });
        List<BunkerInfo> bunkers = new ArrayList<>();
        read(manager, "bunkers.json", errors).ifPresent(root -> {
            for (JsonElement element : GsonHelper.getAsJsonArray(root, "bunkers", new JsonArray())) {
                try {
                    JsonObject o = element.getAsJsonObject();
                    List<String> features = new ArrayList<>();
                    for (JsonElement f : GsonHelper.getAsJsonArray(o, "features", new JsonArray())) {
                        features.add(f.getAsString());
                    }
                    bunkers.add(new BunkerInfo(GsonHelper.getAsString(o, "id"), GsonHelper.getAsString(o, "title"),
                            GsonHelper.getAsInt(o, "area"), GsonHelper.getAsString(o, "supplies"),
                            GsonHelper.getAsString(o, "duration"), List.copyOf(features)));
                } catch (RuntimeException e) {
                    errors.add("bunkers: bad entry " + element + ": " + e.getMessage());
                }
            }
        });
        if (catastrophes.isEmpty()) errors.add("no catastrophes");
        if (bunkers.isEmpty()) errors.add("no bunkers");
        return new Contents(cards, List.copyOf(catastrophes), List.copyOf(bunkers), List.copyOf(errors));
    }

    private static Optional<JsonObject> read(ResourceManager manager, String path, List<String> errors) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
        Optional<Resource> resource = manager.getResource(location);
        if (resource.isEmpty()) {
            errors.add("missing data/" + NAMESPACE + "/" + path);
            return Optional.empty();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return Optional.of(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            errors.add("cannot read data/" + NAMESPACE + "/" + path + ": " + e.getMessage());
            return Optional.empty();
        }
    }
}
