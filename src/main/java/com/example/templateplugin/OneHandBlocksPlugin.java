package com.example.templateplugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.protocol.InteractionType;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;

/**
 * OneHandBlocks Plugin
 * Modifie les blocs pour garder la torche visible en offhand
 */
public class OneHandBlocksPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Configuration
    private final Set<String> BLOCK_CATEGORIES = new HashSet<>(Arrays.asList(
            "Blocks",
            "Blocks.Stone",
            "Blocks.Wood",
            "Blocks.Dirt",
            "Blocks.Sand",
            "Blocks.Metal",
            "Blocks.Ore",
            "Blocks.Decoration",
            "Blocks.Light",
            "Blocks.Glass",
            "Blocks.Natural",
            "Blocks.Rocks"
    ));

    private final Set<String> BLACKLIST_PATTERNS = new HashSet<>(Arrays.asList(
            "Sword",
            "Axe",
            "Pickaxe",
            "Shovel",
            "Hoe",
            "Bow",
            "Crossbow",
            "Shield",
            "Tool"
    ));

    private int modifiedItems = 0;
    private int totalScanned = 0;
    private int matchedItems = 0;

    public OneHandBlocksPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.at(Level.INFO).log("OneHandBlocks Plugin loading...");
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("OneHandBlocks setup called");
    }

    @Override
    protected void start() {
        LOGGER.at(Level.INFO).log("===========================================");
        LOGGER.at(Level.INFO).log("ONEHAND BLOCKS STARTING!");
        LOGGER.at(Level.INFO).log("===========================================");

        new Thread(() -> {
            try {
                Thread.sleep(5000);

                LOGGER.at(Level.INFO).log(">>> Starting item modification...");
                modifyAllItems();

                LOGGER.at(Level.INFO).log("===========================================");
                LOGGER.at(Level.INFO).log("✓ MODIFICATION COMPLETE!");
                LOGGER.at(Level.INFO).log("  Items scanned: " + totalScanned);
                LOGGER.at(Level.INFO).log("  Items matched: " + matchedItems);
                LOGGER.at(Level.INFO).log("  Items modified: " + modifiedItems);
                LOGGER.at(Level.INFO).log("===========================================");
            } catch (InterruptedException e) {
                LOGGER.at(Level.SEVERE).log("Thread interrupted: " + e.getMessage());
            }
        }).start();
    }

    private void modifyAllItems() {
        LOGGER.at(Level.INFO).log(">>> Scanning items...");

        try {
            DefaultAssetMap<String, Item> itemMap = Item.getAssetMap();

            if (itemMap == null) {
                LOGGER.at(Level.SEVERE).log("✗ Unable to retrieve item map!");
                return;
            }

            Map<String, Item> assets = itemMap.getAssetMap();
            totalScanned = assets.size();
            LOGGER.at(Level.INFO).log(">>> Total items in map: " + totalScanned);

            for (Item item : assets.values()) {
                if (shouldModifyItem(item)) {
                    matchedItems++;
                    modifyItem(item);
                }
            }

            LOGGER.at(Level.INFO).log(">>> Scan complete!");

        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("✗ ERROR in modifyAllItems: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void modifyItem(Item item) {
        try {
            String itemId = item.getId();
            boolean modified = false;

            // 1. Changer playerAnimationsId
            Field playerAnimField = findField(item.getClass(), "playerAnimationsId");
            if (playerAnimField != null) {
                playerAnimField.setAccessible(true);
                String currentAnim = (String) playerAnimField.get(item);

                if (currentAnim != null && currentAnim.equals("Block")) {
                    playerAnimField.set(item, "Torch");
                    modified = true;

                    if (modifiedItems < 3) {
                        LOGGER.at(Level.INFO).log("  Changed animation: " + itemId + " (Block -> Torch)");
                    }
                }
            }

            // 2. Modifier les interactions pour enlever Block_Primary et Block_Secondary
            Field interactionsField = findField(item.getClass(), "interactions");
            if (interactionsField != null) {
                interactionsField.setAccessible(true);
                Object interactionsObj = interactionsField.get(item);

                if (interactionsObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<InteractionType, String> interactions = (Map<InteractionType, String>) interactionsObj;

                    // Créer une nouvelle map modifiable
                    Map<InteractionType, String> newInteractions = new EnumMap<>(InteractionType.class);
                    newInteractions.putAll(interactions);

                    boolean interactionChanged = false;

                    // Remplacer Block_Primary par Item_Primary (ou similaire)
                    if (newInteractions.containsKey(InteractionType.Primary)) {
                        String primaryInt = newInteractions.get(InteractionType.Primary);
                        if ("Block_Primary".equals(primaryInt)) {
                            newInteractions.put(InteractionType.Primary, "Item_Primary");
                            interactionChanged = true;
                        }
                    }

                    // Remplacer Block_Secondary par Item_Secondary (ou similaire)
                    if (newInteractions.containsKey(InteractionType.Secondary)) {
                        String secondaryInt = newInteractions.get(InteractionType.Secondary);
                        if ("Block_Secondary".equals(secondaryInt)) {
                            newInteractions.put(InteractionType.Secondary, "Item_Secondary");
                            interactionChanged = true;
                        }
                    }

                    if (interactionChanged) {
                        // Rendre la map immuable
                        newInteractions = Collections.unmodifiableMap(newInteractions);
                        interactionsField.set(item, newInteractions);
                        modified = true;

                        if (modifiedItems < 3) {
                            LOGGER.at(Level.INFO).log("  Changed interactions: " + itemId);
                        }
                    }
                }
            }

            if (modified) {
                // Vider le cache
                try {
                    Field cachedPacketField = findField(item.getClass(), "cachedPacket");
                    if (cachedPacketField != null) {
                        cachedPacketField.setAccessible(true);
                        cachedPacketField.set(item, null);
                    }
                } catch (Exception e) {
                    // Ignorer
                }

                modifiedItems++;

                if (modifiedItems <= 3) {
                    LOGGER.at(Level.INFO).log("✓ Modified: " + itemId);
                }
            }

        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("✗ Error modifying " + item.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private boolean shouldModifyItem(Item item) {
        try {
            String itemId = item.getId();

            // Vérifier la blacklist
            for (String pattern : BLACKLIST_PATTERNS) {
                if (itemId.contains(pattern)) {
                    return false;
                }
            }

            // Vérifier si c'est un bloc
            if (item.hasBlockType()) {
                return true;
            }

            // Vérifier par catégorie
            String[] categories = item.getCategories();
            if (categories != null) {
                for (String category : categories) {
                    if (category != null && category.startsWith("Blocks")) {
                        return true;
                    }
                    if (BLOCK_CATEGORIES.contains(category)) {
                        return true;
                    }
                }
            }

        } catch (Exception e) {
            // Ignorer
        }

        return false;
    }

    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("OneHandBlocks Plugin disabled. Modified " + modifiedItems + " items total.");
    }
}