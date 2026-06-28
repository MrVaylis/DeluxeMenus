package com.extendedclip.deluxemenus.nbt;

import com.extendedclip.deluxemenus.utils.VersionHelper;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

public final class NbtProvider {

    private static final NbtHook NBT_HOOK = createHook();

    private NbtProvider() {
    }

    public static boolean isAvailable() {
        return NBT_HOOK != null;
    }

    /**
     * Sets an NBT tag to the an {@link ItemStack}.
     *
     * @param itemStack The current {@link ItemStack} to be set.
     * @param key       The NBT key to use.
     * @param value     The tag value to set.
     * @return An {@link ItemStack} that has NBT set.
     */
    public static ItemStack setString(final ItemStack itemStack, final String key, final String value) {
        if (itemStack == null) return null;
        if (itemStack.getType() == Material.AIR) return itemStack;

        return withHook(itemStack, hook -> hook.setString(itemStack, key, value));
    }

    /**
     * Sets a boolean to the {@link ItemStack}.
     * Mainly used for setting an item to be unbreakable on older versions.
     *
     * @param itemStack The {@link ItemStack} to set the boolean to.
     * @param key       The key to use.
     * @param value     The boolean value.
     * @return An {@link ItemStack} with a boolean value set.
     */
    public static ItemStack setBoolean(final ItemStack itemStack, final String key, final boolean value) {
        if (itemStack == null) return null;
        if (itemStack.getType() == Material.AIR) return itemStack;

        return withHook(itemStack, hook -> hook.setBoolean(itemStack, key, value));
    }

    /**
     * Gets the NBT tag based on a given key.
     *
     * @param itemStack The {@link ItemStack} to get from.
     * @param key       The key to look for.
     * @return The tag that was stored in the {@link ItemStack}.
     */
    public static String getString(final ItemStack itemStack, final String key) {
        if (itemStack == null) return null;
        if (itemStack.getType() == Material.AIR) return null;
        if (NBT_HOOK == null) return null;

        try {
            return NBT_HOOK.getString(itemStack, key);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    public static ItemStack setByte(final ItemStack itemStack, final String key, final byte value) {
        if (itemStack == null) return null;
        if (itemStack.getType() == Material.AIR) return null;

        return withHook(itemStack, hook -> hook.setByte(itemStack, key, value));
    }

    public static ItemStack setShort(final ItemStack itemStack, final String key, final short value) {
        if (itemStack == null) return null;
        if (itemStack.getType() == Material.AIR) return null;

        return withHook(itemStack, hook -> hook.setShort(itemStack, key, value));
    }

    public static ItemStack setInt(final ItemStack itemStack, final String key, final int value) {
        if (itemStack == null) return null;
        if (itemStack.getType() == Material.AIR) return null;

        return withHook(itemStack, hook -> hook.setInt(itemStack, key, value));
    }

    public static boolean hasKey(final ItemStack itemStack, final String key) {
        if (itemStack == null) return false;
        if (itemStack.getType() == Material.AIR) return false;
        if (NBT_HOOK == null) return false;

        try {
            return NBT_HOOK.hasKey(itemStack, key);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    public static ItemStack removeKey(final ItemStack itemStack, final String key) {
        if (itemStack == null) return null;
        if (itemStack.getType() == Material.AIR) return null;

        return withHook(itemStack, hook -> hook.removeKey(itemStack, key));
    }

    public static Object asNMSCopy(final ItemStack itemStack) {
        if (NBT_HOOK == null) return null;

        try {
            return NBT_HOOK.asNmsCopy(itemStack);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    public static ItemStack asBukkitCopy(final Object nmsItemStack) {
        if (NBT_HOOK == null) return null;

        try {
            return NBT_HOOK.asBukkitCopy(nmsItemStack);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static ItemStack withHook(final ItemStack fallback, final HookOperation operation) {
        if (NBT_HOOK == null) return fallback;

        try {
            final ItemStack itemStack = operation.apply(NBT_HOOK);
            return itemStack == null ? fallback : itemStack;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }

    private static NbtHook createHook() {
        if (VersionHelper.HAS_DATA_COMPONENTS) {
            try {
                return new ModernNbtHook();
            } catch (ReflectiveOperationException ignored) {
            }
        }

        try {
            return new LegacyNbtHook();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Class<?> findCraftItemStackClass() throws ClassNotFoundException {
        try {
            return Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
        } catch (ClassNotFoundException ignored) {
            return VersionHelper.getCraftClass("inventory.CraftItemStack");
        }
    }

    private interface HookOperation {

        ItemStack apply(NbtHook hook) throws ReflectiveOperationException;
    }

    private interface NbtHook {

        ItemStack setString(ItemStack itemStack, String key, String value) throws ReflectiveOperationException;

        ItemStack setBoolean(ItemStack itemStack, String key, boolean value) throws ReflectiveOperationException;

        ItemStack setByte(ItemStack itemStack, String key, byte value) throws ReflectiveOperationException;

        ItemStack setShort(ItemStack itemStack, String key, short value) throws ReflectiveOperationException;

        ItemStack setInt(ItemStack itemStack, String key, int value) throws ReflectiveOperationException;

        String getString(ItemStack itemStack, String key) throws ReflectiveOperationException;

        boolean hasKey(ItemStack itemStack, String key) throws ReflectiveOperationException;

        ItemStack removeKey(ItemStack itemStack, String key) throws ReflectiveOperationException;

        Object asNmsCopy(ItemStack itemStack) throws ReflectiveOperationException;

        ItemStack asBukkitCopy(Object nmsItemStack) throws ReflectiveOperationException;
    }

    private static final class ModernNbtHook implements NbtHook {

        private final Class<?> compoundClass;
        private final Object customDataComponentType;
        private final Method asNmsCopyMethod;
        private final Method asBukkitCopyMethod;
        private final Method getComponentMethod;
        private final Method setComponentMethod;
        private final Method removeComponentMethod;
        private final Method customDataOfMethod;
        private final Method customDataCopyTagMethod;
        private final Method putStringMethod;
        private final Method putBooleanMethod;
        private final Method putByteMethod;
        private final Method putShortMethod;
        private final Method putIntMethod;
        private final Method containsMethod;
        private final Method removeTagMethod;
        private final Method isEmptyMethod;
        private final Method getStringMethod;
        private final Method getStringOrMethod;

        private ModernNbtHook() throws ReflectiveOperationException {
            final Class<?> dataComponentTypeClass = Class.forName("net.minecraft.core.component.DataComponentType");
            final Class<?> dataComponentsClass = Class.forName("net.minecraft.core.component.DataComponents");
            final Class<?> itemStackClass = Class.forName("net.minecraft.world.item.ItemStack");
            final Class<?> customDataClass = Class.forName("net.minecraft.world.item.component.CustomData");
            final Class<?> craftItemStackClass = findCraftItemStackClass();

            compoundClass = Class.forName("net.minecraft.nbt.CompoundTag");
            customDataComponentType = dataComponentsClass.getField("CUSTOM_DATA").get(null);

            asNmsCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            asBukkitCopyMethod = craftItemStackClass.getMethod("asBukkitCopy", itemStackClass);
            getComponentMethod = itemStackClass.getMethod("get", dataComponentTypeClass);
            setComponentMethod = itemStackClass.getMethod("set", dataComponentTypeClass, Object.class);
            removeComponentMethod = itemStackClass.getMethod("remove", dataComponentTypeClass);
            customDataOfMethod = customDataClass.getMethod("of", compoundClass);
            customDataCopyTagMethod = customDataClass.getMethod("copyTag");

            putStringMethod = compoundClass.getMethod("putString", String.class, String.class);
            putBooleanMethod = compoundClass.getMethod("putBoolean", String.class, boolean.class);
            putByteMethod = compoundClass.getMethod("putByte", String.class, byte.class);
            putShortMethod = compoundClass.getMethod("putShort", String.class, short.class);
            putIntMethod = compoundClass.getMethod("putInt", String.class, int.class);
            containsMethod = compoundClass.getMethod("contains", String.class);
            removeTagMethod = compoundClass.getMethod("remove", String.class);
            isEmptyMethod = compoundClass.getMethod("isEmpty");
            getStringMethod = findMethod(compoundClass, "getString", String.class);
            getStringOrMethod = findMethod(compoundClass, "getStringOr", String.class, String.class);
            if (getStringMethod == null && getStringOrMethod == null) {
                throw new NoSuchMethodException("CompoundTag#getString or CompoundTag#getStringOr");
            }
        }

        @Override
        public ItemStack setString(final ItemStack itemStack, final String key, final String value) throws ReflectiveOperationException {
            return update(itemStack, compound -> putStringMethod.invoke(compound, key, value));
        }

        @Override
        public ItemStack setBoolean(final ItemStack itemStack, final String key, final boolean value) throws ReflectiveOperationException {
            return update(itemStack, compound -> putBooleanMethod.invoke(compound, key, value));
        }

        @Override
        public ItemStack setByte(final ItemStack itemStack, final String key, final byte value) throws ReflectiveOperationException {
            return update(itemStack, compound -> putByteMethod.invoke(compound, key, value));
        }

        @Override
        public ItemStack setShort(final ItemStack itemStack, final String key, final short value) throws ReflectiveOperationException {
            return update(itemStack, compound -> putShortMethod.invoke(compound, key, value));
        }

        @Override
        public ItemStack setInt(final ItemStack itemStack, final String key, final int value) throws ReflectiveOperationException {
            return update(itemStack, compound -> putIntMethod.invoke(compound, key, value));
        }

        @Override
        public String getString(final ItemStack itemStack, final String key) throws ReflectiveOperationException {
            final Object compound = copyCustomDataTag(asNmsCopy(itemStack));

            if (getStringOrMethod != null) {
                return (String) getStringOrMethod.invoke(compound, key, null);
            }

            final Object value = getStringMethod.invoke(compound, key);
            if (value instanceof Optional<?> optional) {
                return optional.map(Object::toString).orElse(null);
            }

            return (String) value;
        }

        @Override
        public boolean hasKey(final ItemStack itemStack, final String key) throws ReflectiveOperationException {
            return (boolean) containsMethod.invoke(copyCustomDataTag(asNmsCopy(itemStack)), key);
        }

        @Override
        public ItemStack removeKey(final ItemStack itemStack, final String key) throws ReflectiveOperationException {
            return update(itemStack, compound -> removeTagMethod.invoke(compound, key));
        }

        @Override
        public Object asNmsCopy(final ItemStack itemStack) throws ReflectiveOperationException {
            return asNmsCopyMethod.invoke(null, itemStack);
        }

        @Override
        public ItemStack asBukkitCopy(final Object nmsItemStack) throws ReflectiveOperationException {
            return (ItemStack) asBukkitCopyMethod.invoke(null, nmsItemStack);
        }

        private ItemStack update(final ItemStack itemStack, final CompoundOperation operation) throws ReflectiveOperationException {
            final Object nmsItemStack = asNmsCopy(itemStack);
            final Object compound = copyCustomDataTag(nmsItemStack);

            operation.apply(compound);

            if ((boolean) isEmptyMethod.invoke(compound)) {
                removeComponentMethod.invoke(nmsItemStack, customDataComponentType);
            } else {
                setComponentMethod.invoke(nmsItemStack, customDataComponentType, customDataOfMethod.invoke(null, compound));
            }

            return asBukkitCopy(nmsItemStack);
        }

        private Object copyCustomDataTag(final Object nmsItemStack) throws ReflectiveOperationException {
            final Object customData = getComponentMethod.invoke(nmsItemStack, customDataComponentType);
            if (customData == null) {
                return compoundClass.getDeclaredConstructor().newInstance();
            }

            return customDataCopyTagMethod.invoke(customData);
        }
    }

    private static final class LegacyNbtHook implements NbtHook {

        private final Constructor<?> nbtCompoundConstructor;
        private final Method getStringMethod;
        private final Method setStringMethod;
        private final Method setBooleanMethod;
        private final Method setByteMethod;
        private final Method setShortMethod;
        private final Method setIntMethod;
        private final Method removeTagMethod;
        private final Method containsMethod;
        private final Method hasTagMethod;
        private final Method getTagMethod;
        private final Method setTagMethod;
        private final Method asNmsCopyMethod;
        private final Method asBukkitCopyMethod;

        private LegacyNbtHook() throws ReflectiveOperationException {
            final Class<?> compoundClass = VersionHelper.getNMSClass("nbt", "NBTTagCompound");
            final Class<?> itemStackClass = VersionHelper.getNMSClass("world.item", "ItemStack");
            final Class<?> inventoryClass = findCraftItemStackClass();

            containsMethod = compoundClass.getMethod(VersionConstants.CONTAINS_METHOD_NAME, String.class);
            getStringMethod = compoundClass.getMethod(VersionConstants.GET_STRING_METHOD_NAME, String.class);
            setStringMethod = compoundClass.getMethod(VersionConstants.SET_STRING_METHOD_NAME, String.class, String.class);
            setBooleanMethod = compoundClass.getMethod(VersionConstants.SET_BOOLEAN_METHOD_NAME, String.class, boolean.class);
            setByteMethod = compoundClass.getMethod(VersionConstants.SET_BYTE_METHOD_NAME, String.class, byte.class);
            setShortMethod = compoundClass.getMethod(VersionConstants.SET_SHORT_METHOD_NAME, String.class, short.class);
            setIntMethod = compoundClass.getMethod(VersionConstants.SET_INTEGER_METHOD_NAME, String.class, int.class);
            removeTagMethod = compoundClass.getMethod(VersionConstants.REMOVE_TAG_METHOD_NAME, String.class);
            hasTagMethod = itemStackClass.getMethod(VersionConstants.HAS_TAG_METHOD_NAME);
            getTagMethod = itemStackClass.getMethod(VersionConstants.GET_TAG_METHOD_NAME);
            setTagMethod = itemStackClass.getMethod(VersionConstants.SET_TAG_METHOD_NAME, compoundClass);
            nbtCompoundConstructor = compoundClass.getDeclaredConstructor();

            asNmsCopyMethod = inventoryClass.getMethod("asNMSCopy", ItemStack.class);
            asBukkitCopyMethod = inventoryClass.getMethod("asBukkitCopy", itemStackClass);
        }

        @Override
        public ItemStack setString(final ItemStack itemStack, final String key, final String value) throws ReflectiveOperationException {
            return update(itemStack, compound -> setStringMethod.invoke(compound, key, value));
        }

        @Override
        public ItemStack setBoolean(final ItemStack itemStack, final String key, final boolean value) throws ReflectiveOperationException {
            return update(itemStack, compound -> setBooleanMethod.invoke(compound, key, value));
        }

        @Override
        public ItemStack setByte(final ItemStack itemStack, final String key, final byte value) throws ReflectiveOperationException {
            return update(itemStack, compound -> setByteMethod.invoke(compound, key, value));
        }

        @Override
        public ItemStack setShort(final ItemStack itemStack, final String key, final short value) throws ReflectiveOperationException {
            return update(itemStack, compound -> setShortMethod.invoke(compound, key, value));
        }

        @Override
        public ItemStack setInt(final ItemStack itemStack, final String key, final int value) throws ReflectiveOperationException {
            return update(itemStack, compound -> setIntMethod.invoke(compound, key, value));
        }

        @Override
        public String getString(final ItemStack itemStack, final String key) throws ReflectiveOperationException {
            return (String) getStringMethod.invoke(getOrCreateTag(asNmsCopy(itemStack)), key);
        }

        @Override
        public boolean hasKey(final ItemStack itemStack, final String key) throws ReflectiveOperationException {
            return (boolean) containsMethod.invoke(getOrCreateTag(asNmsCopy(itemStack)), key);
        }

        @Override
        public ItemStack removeKey(final ItemStack itemStack, final String key) throws ReflectiveOperationException {
            final Object nmsItemStack = asNmsCopy(itemStack);
            if (!(boolean) hasTagMethod.invoke(nmsItemStack)) return itemStack;

            final Object compound = getOrCreateTag(nmsItemStack);
            removeTagMethod.invoke(compound, key);
            setTagMethod.invoke(nmsItemStack, compound);

            return asBukkitCopy(nmsItemStack);
        }

        @Override
        public Object asNmsCopy(final ItemStack itemStack) throws ReflectiveOperationException {
            return asNmsCopyMethod.invoke(null, itemStack);
        }

        @Override
        public ItemStack asBukkitCopy(final Object nmsItemStack) throws ReflectiveOperationException {
            return (ItemStack) asBukkitCopyMethod.invoke(null, nmsItemStack);
        }

        private ItemStack update(final ItemStack itemStack, final CompoundOperation operation) throws ReflectiveOperationException {
            final Object nmsItemStack = asNmsCopy(itemStack);
            final Object compound = getOrCreateTag(nmsItemStack);

            operation.apply(compound);
            setTagMethod.invoke(nmsItemStack, compound);

            return asBukkitCopy(nmsItemStack);
        }

        private Object getOrCreateTag(final Object nmsItemStack) throws ReflectiveOperationException {
            if ((boolean) hasTagMethod.invoke(nmsItemStack)) {
                return getTagMethod.invoke(nmsItemStack);
            }

            return nbtCompoundConstructor.newInstance();
        }
    }

    private interface CompoundOperation {

        void apply(Object compound) throws ReflectiveOperationException;
    }

    private static Method findMethod(final Class<?> clazz, final String methodName, final Class<?>... parameterTypes) {
        try {
            return clazz.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static final class VersionConstants {

        private final static String CONTAINS_METHOD_NAME = containsMethodName();
        private final static String GET_STRING_METHOD_NAME = getStringMethodName();
        private final static String SET_STRING_METHOD_NAME = setStringMethodName();
        private final static String SET_BOOLEAN_METHOD_NAME = setBooleanMethodName();
        private final static String SET_BYTE_METHOD_NAME = setByteMethodName();
        private final static String SET_SHORT_METHOD_NAME = setShortMethodName();
        private final static String SET_INTEGER_METHOD_NAME = setIntegerMethodName();
        private final static String REMOVE_TAG_METHOD_NAME = removeTagMethodName();
        private final static String HAS_TAG_METHOD_NAME = hasTagMethodName();
        private final static String GET_TAG_METHOD_NAME = getTagMethodName();
        private final static String SET_TAG_METHOD_NAME = setTagMethodName();

        private static String getStringMethodName() {
            if (VersionHelper.HAS_OBFUSCATED_NAMES) return "l";
            return "getString";
        }

        private static String setStringMethodName() {
            if (VersionHelper.HAS_OBFUSCATED_NAMES) return "a";
            return "setString";
        }

        private static String setBooleanMethodName() {
            if (VersionHelper.HAS_OBFUSCATED_NAMES) return "a";
            return "setBoolean";
        }

        private static String setByteMethodName() {
            if (VersionHelper.HAS_OBFUSCATED_NAMES) return "a";
            return "setByte";
        }

        private static String setShortMethodName() {
            if (VersionHelper.HAS_OBFUSCATED_NAMES) return "a";
            return "setShort";
        }

        private static String setIntegerMethodName() {
            if (VersionHelper.HAS_OBFUSCATED_NAMES) return "a";
            return "setInt";
        }

        private static String hasTagMethodName() {
            if (VersionHelper.CURRENT_VERSION >= 1200) return "u"; // 1.20 variable change
            if (VersionHelper.CURRENT_VERSION >= 1190) return "t"; // 1.19 variable change
            if (VersionHelper.CURRENT_VERSION == 1182) return "s"; // 1.18.2 variable change
            if (VersionHelper.HAS_OBFUSCATED_NAMES) return "r"; // 1.18-1.18.1
            return "hasTag";
        }

        private static String getTagMethodName() {
            if (VersionHelper.CURRENT_VERSION >= 1200) return "v"; // 1.20 variable change
            if (VersionHelper.CURRENT_VERSION >= 1190) return "u"; // 1.19 variable change
            if (VersionHelper.CURRENT_VERSION == 1182) return "t"; // 1.18.2 variable change
            if (VersionHelper.HAS_OBFUSCATED_NAMES) return "s"; // 1.18-1.18.1
            return "getTag";
        }

        private static String containsMethodName() {
            if (VersionHelper.HAS_OBFUSCATED_NAMES) return "e";
            return "hasKey";
        }

        private static String setTagMethodName() {
            if (VersionHelper.HAS_OBFUSCATED_NAMES) return "c";
            return "setTag";
        }

        private static String removeTagMethodName() {
            if (VersionHelper.HAS_OBFUSCATED_NAMES) return "r";
            return "remove";
        }

    }
}
