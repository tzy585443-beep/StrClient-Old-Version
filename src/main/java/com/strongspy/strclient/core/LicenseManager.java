package com.strongspy.strclient.core;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class LicenseManager {

    // ── Secure Salt ──────────────────────────────────────────────────
    // Note: This MUST exactly match the SALT defined in your generator.html!
    private static final String SALT = "StrClient_Secure_Salt_2026";
    // ──────────────────────────────────────────────────────────────────

    // 10-minute validity window for dynamic keys (in milliseconds)
    private static final long TIME_STEP_MS = 600000L;

    // Save path for the local license file
    private static final Path KEY_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("strclient").resolve(".license");

    private static boolean unlocked = false;
    private static String cacheExpireTimeStr = ""; // Cached string for UI rendering

    private LicenseManager() {}

    public static boolean isUnlocked() { return unlocked; }

    /**
     * Gets the formatted expiration string for UI rendering.
     */
    public static String getExpireTimeStr() {
        return cacheExpireTimeStr;
    }

    /**
     * Invoked upon game startup to check whether a valid, non-expired,
     * and untampered device credential exists locally.
     */
    public static boolean checkSaved() {
        if (!Files.exists(KEY_FILE)) return false;
        try {
            // Read local credential (Format: DeviceToken|ExpirationInfo)
            String content = Files.readString(KEY_FILE).trim();
            String[] parts = content.split("\\|");

            // If format doesn't match the standard, mark as invalid
            if (parts.length != 2) return false;

            String savedToken = parts[0];
            String expirePart = parts[1];

            // 1. Check for lifetime/eternal entitlement
            if (expirePart.equalsIgnoreCase("ETERNAL")) {
                cacheExpireTimeStr = "Forever (Unlimited)";
            } else {
                // If not eternal, perform epoch timestamp expiration check
                long expireTime = Long.parseLong(expirePart);
                if (System.currentTimeMillis() > expireTime) {
                    Files.deleteIfExists(KEY_FILE); // Auto-cleanup expired file
                    cacheExpireTimeStr = "";
                    return false;
                }
                cacheExpireTimeStr = formatTime(expireTime);
            }

            // 2. Recalculate and match the hardware device fingerprint
            String expectedToken = generateDeviceToken();
            if (expectedToken.equalsIgnoreCase(savedToken)) {
                unlocked = true;
                return true;
            }
        } catch (Exception ignored) {
            // Safe guard against structural parsing errors or corrupt files
        }
        return false;
    }

    /**
     * Submits and validates a 5x5 dynamic key entered by the user.
     */
    public static boolean submit(String input) {
        String cleanInput = input.trim().replace("-", "").toUpperCase();
        if (cleanInput.length() != 25) return false;

        // Extract first character as type identifier
        char typeChar = cleanInput.charAt(0);
        String typeStr;
        boolean isEternal = false;
        long durationMs = 0L;

        switch (typeChar) {
            case 'D': typeStr = "DAY"; durationMs = 24L * 60 * 60 * 1000; break; // 1 Day
            case 'M': typeStr = "MONTH"; durationMs = 30L * 24 * 60 * 60 * 1000; break; // 1 Month
            case 'Y': typeStr = "YEAR"; durationMs = 365L * 24 * 60 * 60 * 1000; break; // 1 Year
            case 'E': typeStr = "ETERNAL"; isEternal = true; break; // Permanent/Lifetime flag
            default: return false; // Invalid flag, reject immediately
        }

        long currentTimestamp = System.currentTimeMillis();
        long currentInterval = currentTimestamp / TIME_STEP_MS;

        boolean isValid = false;

        // 1. Verify against current time interval step
        if (cleanInput.equals(generateDynamicCode(currentInterval, typeChar, typeStr))) {
            isValid = true;
        }
        // 2. Tolerance mechanism: Verify against previous interval step (network/typing offset fallback)
        else if (cleanInput.equals(generateDynamicCode(currentInterval - 1, typeChar, typeStr))) {
            isValid = true;
        }

        if (isValid) {
            unlocked = true;
            try {
                Files.createDirectories(KEY_FILE.getParent());

                String expireValue;
                if (isEternal) {
                    expireValue = "ETERNAL";
                    cacheExpireTimeStr = "Forever (Unlimited)";
                } else {
                    long expireTimestamp = System.currentTimeMillis() + durationMs;
                    expireValue = String.valueOf(expireTimestamp);
                    cacheExpireTimeStr = formatTime(expireTimestamp);
                }

                // Persist securely: Hardware Token | Expiration Data
                String fileContent = generateDeviceToken() + "|" + expireValue;
                Files.writeString(KEY_FILE, fileContent);
            } catch (IOException ignored) {}
            return true;
        }

        return false;
    }

    /**
     * Dynamically constructs the unique 25-character sequence based on interval, type, and salt.
     */
    private static String generateDynamicCode(long interval, char typeChar, String typeStr) {
        try {
            String message = interval + SALT + typeStr;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(message.getBytes(StandardCharsets.UTF_8));

            char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
            StringBuilder sb = new StringBuilder();

            // Prefix key with the exact classification character
            sb.append(typeChar);

            for (int i = 1; i < 25; i++) {
                int byteValue = hashBytes[i % hashBytes.length] & 0xFF;
                int index = (byteValue + i) % alphabet.length;
                sb.append(alphabet[index]);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }
    }

    /**
     * Generates a device-bound security token unique to the current operating machine environment.
     */
    private static String generateDeviceToken() {
        try {
            String deviceInfo = System.getProperty("user.name") +
                    System.getProperty("os.arch") +
                    SALT +
                    "Licensed_Successfully";

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(deviceInfo.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "fallback_token_hash_code";
        }
    }

    /**
     * Converts a millisecond timestamp to a human-readable date format.
     */
    private static String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }
}