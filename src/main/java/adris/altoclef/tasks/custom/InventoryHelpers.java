package adris.altoclef.tasks.custom;

import adris.altoclef.AltoClef;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;

/**
 * Mapping-robust helpers for detecting enchanted books with a target enchantment (MC 1.21.x).
 * - Uses DataComponentTypes.STORED_ENCHANTMENTS (ItemEnchantmentsComponent)
 * - Avoids compile-time dependency on ENCHANTMENT registry generics
 * - Handles Set/Map/fastutil via reflection and compares by Identifier
 */
public final class InventoryHelpers {

    private InventoryHelpers() {}

    /* ========== Public API ========== */

    /** true iff stack is an ENCHANTED_BOOK that contains an enchantment whose id/path matches query. */
    public static boolean isEnchantedBookWith(ItemStack stack, String query) {
        if (stack == null || !stack.isOf(Items.ENCHANTED_BOOK)) return false;
        Set<String> want = normalizeQuery(query);
        ItemEnchantmentsComponent comp = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (comp == null) return false;

        for (Identifier id : extractEnchantmentIds(comp)) {
            if (id == null) continue;
            String full = id.toString(); // e.g. "minecraft:mending"
            String path = id.getPath();  // e.g. "mending"
            if (want.contains(full) || want.contains(path)) return true;
            // allow suffix matching for custom namespaces
            for (String w : want) if (path.equals(w) || path.endsWith(w)) return true;
        }
        return false;
    }

    public static boolean hasEnchantedBook(AltoClef mod, String query) {
        return countEnchantedBooks(mod, query) > 0;
    }

    public static int countEnchantedBooks(AltoClef mod, String query) {
        ClientPlayerEntity p = mod.getPlayer();
        if (p == null) return 0;
        PlayerInventory inv = p.getInventory();
        int c = 0;
        for (ItemStack s : inv.main) if (isEnchantedBookWith(s, query)) c += s.getCount();
        if (!inv.offHand.isEmpty() && isEnchantedBookWith(inv.offHand.get(0), query))
            c += inv.offHand.get(0).getCount();
        return c;
    }

    /** predicate for deposit tasks (keeps only wanted enchanted books). */
    public static Predicate<ItemStack> filterFor(String query) {
        return stack -> isEnchantedBookWith(stack, query);
    }

    /* ========== Internals ========== */

    /** Extract enchantment IDs from component regardless of mapping differences. */
    private static List<Identifier> extractEnchantmentIds(ItemEnchantmentsComponent comp) {
        List<Identifier> out = new ArrayList<>();
        try {
            Object store = invokeNoArg(comp, "getEnchantments");     // Yarn variant A
            if (store == null) store = invokeNoArg(comp, "enchantments"); // Yarn variant B

            Object keys = store;
            if (!(keys instanceof Set)) {
                Object ks = invokeNoArg(store, "keySet"); // fastutil map → keySet
                if (ks instanceof Set) keys = ks;
            }

            if (keys instanceof Set<?> set) {
                for (Object entry : set) {
                    Identifier id = tryExtractIdFromRegistryEntry(entry);
                    if (id != null) out.add(id);
                }
            } else if (keys instanceof Iterable<?> it) {
                for (Object entry : it) {
                    Identifier id = tryExtractIdFromRegistryEntry(entry);
                    if (id != null) out.add(id);
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /**
     * Works for RegistryEntry<Enchantment> in different mappings.
     * Tries entry.getKey().map(RegistryKey::getValue) → Identifier.
     * Fallback: parse from entry.toString() like "minecraft:mending".
     */
    private static Identifier tryExtractIdFromRegistryEntry(Object entry) {
        if (entry == null) return null;
        try {
            Object opt = invokeNoArg(entry, "getKey"); // Optional<RegistryKey<T>>
            if (opt instanceof Optional<?> o) {
                Object key = o.orElse(null);
                if (key != null) {
                    Object id = invokeNoArg(key, "getValue");
                    if (id instanceof Identifier ident) return ident;
                }
            }
        } catch (Throwable ignored) {}

        // best-effort parse from toString if it contains "namespace:path"
        String s = String.valueOf(entry).toLowerCase(Locale.ROOT);
        int i = s.indexOf(':');
        if (i > 0) {
            String ns = s.substring(0, i).replaceAll("[^a-z0-9._-]", "");
            String path = s.substring(i + 1).replaceAll("[^a-z0-9/._-]", "");
            return makeId(ns, path); // <-- use factory, not constructor
        }
        return null;
    }

    /** Accepts "mending", "minecraft:mending", "feather falling", "sharpness_v", etc. */
    private static Set<String> normalizeQuery(String query) {
        Set<String> out = new HashSet<>();
        if (query == null) return out;
        String q = query.trim().toLowerCase(Locale.ROOT).replace(' ', '_');

        out.add(q); // path only
        if (!q.contains(":")) out.add("minecraft:" + q);

        String s = stripRomanLevel(q);
        out.add(s);
        if (!s.contains(":")) out.add("minecraft:" + s);
        return out;
    }

    private static String stripRomanLevel(String s) {
        if (s.endsWith("_iii")) return s.substring(0, s.length() - 4);
        if (s.endsWith("_ii"))  return s.substring(0, s.length() - 3);
        if (s.endsWith("_iv"))  return s.substring(0, s.length() - 3);
        if (s.endsWith("_v"))   return s.substring(0, s.length() - 2);
        if (s.endsWith("_i"))   return s.substring(0, s.length() - 2);
        return s;
    }

    /* reflection helpers */
    private static Object invokeNoArg(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable t) { return null; }
    }

    /** Uses Identifier.of(ns,path); falls back to tryParse if unavailable. */
    private static Identifier makeId(String ns, String path) {
        try { return Identifier.of(ns, path); } catch (Throwable ignored) {}
        return Identifier.tryParse(ns + ":" + path);
    }
}
