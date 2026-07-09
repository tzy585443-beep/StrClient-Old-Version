package com.strongspy.strclient.core;

/**
 * 类型化设置项。支持 Double、Boolean、String（枚举式）三种类型。
 */
public class ModuleSetting<T> {

    public enum Type { DOUBLE, INT, BOOLEAN, STRING }

    private final String key;
    private final String displayName;
    private final String description;
    private final Type type;
    private T value;
    private final T defaultValue;
    private final double min;
    private final double max;
    private final String[] options;

    // ── 工厂方法 ──────────────────────────────────────────────────────

    public static ModuleSetting<Double> ofDouble(
            String key, String displayName, String description,
            double defaultValue, double min, double max) {
        return new ModuleSetting<>(key, displayName, description,
                Type.DOUBLE, defaultValue, min, max, null);
    }

    /** 整数滑块，WebUI 显示为不带小数的整数值（步长固定为 1） */
    public static ModuleSetting<Double> ofInt(
            String key, String displayName, String description,
            int defaultValue, int min, int max) {
        return new ModuleSetting<>(key, displayName, description,
                Type.INT, (double) defaultValue, min, max, null);
    }

    public static ModuleSetting<Boolean> ofBoolean(
            String key, String displayName, String description, boolean defaultValue) {
        return new ModuleSetting<>(key, displayName, description,
                Type.BOOLEAN, defaultValue, 0, 0, null);
    }

    public static ModuleSetting<String> ofString(
            String key, String displayName, String description,
            String defaultValue, String... options) {
        return new ModuleSetting<>(key, displayName, description,
                Type.STRING, defaultValue, 0, 0, options);
    }

    @SuppressWarnings("unchecked")
    private ModuleSetting(String key, String displayName, String description,
                          Type type, T defaultValue, double min, double max, String[] options) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.min = min;
        this.max = max;
        this.options = options;
    }

    // ── 值管理 ────────────────────────────────────────────────────────

    public T getValue() { return value; }
    public T getDefaultValue() { return defaultValue; }

    @SuppressWarnings("unchecked")
    public void setFromObject(Object obj) {
        try {
            switch (type) {
                case DOUBLE -> {
                    double d = Math.max(min, Math.min(max, ((Number) obj).doubleValue()));
                    value = (T) Double.valueOf(d);
                }
                case INT -> {
                    double d = Math.max(min, Math.min(max, Math.round(((Number) obj).doubleValue())));
                    value = (T) Double.valueOf(d);
                }
                case BOOLEAN -> {
                    String s = obj.toString();
                    value = (T) Boolean.valueOf(s.equals("true") || s.equals("1"));
                }
                case STRING -> {
                    String s = obj.toString();
                    if (options != null) {
                        for (String opt : options) {
                            if (opt.equalsIgnoreCase(s)) { value = (T) opt; return; }
                        }
                    } else {
                        value = (T) s;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    // ── Getters ───────────────────────────────────────────────────────

    public String getKey()         { return key; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Type getType()          { return type; }
    public double getMin()         { return min; }
    public double getMax()         { return max; }
    public String[] getOptions()   { return options; }
}
