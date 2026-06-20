package com.extendedclip.deluxemenus.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

public class SoundUtils {

    public static Sound getSound(String name) {
        try {
            // As of Minecraft 1.21.3, the org.bukkit.Sound class type changed from Enum to Interface.
            // This fixes java.lang.IncompatibleClassChangeError when trying to use versions prior to 1.21.3.
            Method valueOfMethod = Class.forName("org.bukkit.Sound").getMethod("valueOf", String.class);
            return (Sound) valueOfMethod.invoke(null, name);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            final Sound sound = getRegistrySound(name);
            if (sound != null) {
                return sound;
            }

            throw new IllegalArgumentException("No sound found for " + name, e);
        }
    }

    private static Sound getRegistrySound(String name) {
        final String normalizedName = name.toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(normalizedName);
        Sound sound = key == null ? null : Registry.SOUNDS.get(key);

        if (sound == null && !normalizedName.contains(":")) {
            sound = Registry.SOUNDS.get(NamespacedKey.minecraft(normalizedName));
        }

        if (sound == null) {
            final String legacyName = normalizedName.replace('_', '.');
            key = NamespacedKey.fromString(legacyName);
            sound = key == null ? null : Registry.SOUNDS.get(key);

            if (sound == null && !legacyName.contains(":")) {
                sound = Registry.SOUNDS.get(NamespacedKey.minecraft(legacyName));
            }
        }

        return sound;
    }
}
