package com.strongspy.strclient;

import com.strongspy.strclient.core.*;
import com.strongspy.strclient.gui.HudEditScreen;
import com.strongspy.strclient.gui.StrClientScreen;
import com.strongspy.strclient.gui.LicenseScreen;
import com.strongspy.strclient.core.LicenseManager;
import com.strongspy.strclient.modules.killaura.KillAuraModule;
import com.strongspy.strclient.modules.jumpmodify.JumpModifyModule;
import com.strongspy.strclient.modules.pausefall.PauseFallModule;
import com.strongspy.strclient.modules.scaffold.ScaffoldModule;
import com.strongspy.strclient.modules.flight.FlightModule;
import com.strongspy.strclient.modules.nofall.NoFallModule;
import com.strongspy.strclient.modules.autotaunt.AutoTauntModule;
import com.strongspy.strclient.modules.speed.SpeedModule;
import com.strongspy.strclient.modules.jesus.JesusModule;
import com.strongspy.strclient.modules.packetmine.PacketMineModule;
import com.strongspy.strclient.modules.autosteal.AutoStealModule;
import com.strongspy.strclient.modules.fullbright.FullBrightModule;
import com.strongspy.strclient.modules.fastbreak.FastBreakModule;
import com.strongspy.strclient.modules.autorespawn.AutoRespawnModule;
import com.strongspy.strclient.modules.chestesp.ChestEspModule;
import com.strongspy.strclient.modules.watermark.WatermarkModule;
import com.strongspy.strclient.modules.keystroke.KeystrokeModule;
import com.strongspy.strclient.modules.island.DynamicIslandModule;
import com.strongspy.strclient.modules.capepatch.CapePatchModule;
import com.strongspy.strclient.modules.autof5.AutoF5Module;
import com.strongspy.strclient.modules.spinner.SpinnerModule;
import com.strongspy.strclient.modules.orbit.OrbitModule;
import com.strongspy.strclient.modules.freelook.FreelookModule;
import com.strongspy.strclient.modules.autosprint.AutoSprintModule;
import com.strongspy.strclient.modules.aimassist.AimAssistModule;
import com.strongspy.strclient.modules.airjump.AirJumpModule;
import com.strongspy.strclient.modules.musicplayer.MusicPlayerModule;
import com.strongspy.strclient.modules.targethud.TargetHUDModule;
import com.strongspy.strclient.modules.antipowdersnow.AntiPowderSnowModule;
import com.strongspy.strclient.modules.bedbreaker.BedBreakerModule;
import com.strongspy.strclient.modules.keyhints.KeyHintsModule;
import com.strongspy.strclient.modules.creativeflight.CreativeFlightModule;
import com.strongspy.strclient.modules.autocrystal.AutoCrystalModule;
import com.strongspy.strclient.modules.autorespawnanchor.AutoRespawnAnchorModule;
import com.strongspy.strclient.modules.crash.CrashModule;
import com.strongspy.strclient.modules.terminal.TerminalModule;
import com.strongspy.strclient.web.WebServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class StrClientMod implements ClientModInitializer {
    public static final String MOD_ID = "strclient";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static StrClientMod INSTANCE;
    private ModuleManager moduleManager;
    private SettingsManager settingsManager;
    private WebServer webServer;

    private static final String KEYBIND_CATEGORY = "StrClient Modules";

    // ── Window Title & Asset Configuration ───────────────────────────────
    private static final String WINDOW_TITLE = "StrClient 1.21.1 r1.6.0";
    private static final String ICON_PATH = "/assets/strclient/icon.png";
    // ──────────────────────────────────────────────────────────────────

    private static boolean hasCheckedLicense = false;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        LOGGER.info("[StrClient] Initializing...");

        settingsManager = new SettingsManager();
        moduleManager = new ModuleManager(settingsManager);

        // Registering Functionality Modules
        moduleManager.register(new KillAuraModule());
        moduleManager.register(new ScaffoldModule());
        moduleManager.register(new FlightModule());
        moduleManager.register(new NoFallModule());
        moduleManager.register(new AutoTauntModule());
        moduleManager.register(new SpeedModule());
        moduleManager.register(new JesusModule());
        moduleManager.register(new PacketMineModule());
        moduleManager.register(new AutoStealModule());
        moduleManager.register(new FullBrightModule());
        moduleManager.register(new FastBreakModule());
        moduleManager.register(new AutoRespawnModule());
        moduleManager.register(new ChestEspModule());
        moduleManager.register(new WatermarkModule());
        moduleManager.register(new KeystrokeModule());
        moduleManager.register(new DynamicIslandModule());
        moduleManager.register(new JumpModifyModule());
        moduleManager.register(new CapePatchModule());
        moduleManager.register(new AutoF5Module());
        moduleManager.register(new SpinnerModule());
        moduleManager.register(new OrbitModule());
        moduleManager.register(new FreelookModule());
        moduleManager.register(new AutoSprintModule());
        moduleManager.register(new AimAssistModule());
        moduleManager.register(new AirJumpModule());
        moduleManager.register(new MusicPlayerModule());
        moduleManager.register(new TargetHUDModule());
        moduleManager.register(new AntiPowderSnowModule());
        moduleManager.register(new BedBreakerModule());
        moduleManager.register(new KeyHintsModule());
        moduleManager.register(new CreativeFlightModule());
        moduleManager.register(new AutoCrystalModule());
        moduleManager.register(new AutoRespawnAnchorModule());
        moduleManager.register(new CrashModule());
        moduleManager.register(new TerminalModule());
        moduleManager.register(new PauseFallModule());

        settingsManager.load(moduleManager);

        // Bind Keybindings for each registered module
        for (AbstractModule module : moduleManager.getAll()) {
            KeyBinding kb = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    module.getDisplayName(),
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,
                    KEYBIND_CATEGORY
            ));
            module.setKeyBinding(kb);
        }

        // Open Client Menu keybinding
        KeyBinding openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Open StrClient Menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KEYBIND_CATEGORY
        ));

        // Adjust HUD Layout keybinding
        KeyBinding hudEditKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Edit HUD Layout",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KEYBIND_CATEGORY
        ));

        webServer = new WebServer(moduleManager, settingsManager);
        webServer.start(5000);

        new HudOverlay(moduleManager).register();

        // Application Lifecyle Event: Window customization & Post-Load Initialization
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            long handle = mc.getWindow().getHandle();
            GLFW.glfwSetWindowTitle(handle, WINDOW_TITLE);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w        = stack.mallocInt(1);
                IntBuffer h        = stack.mallocInt(1);
                IntBuffer channels = stack.mallocInt(1);

                InputStream stream = StrClientMod.class.getResourceAsStream(ICON_PATH);
                if (stream != null) {
                    byte[]     bytes  = stream.readAllBytes();
                    ByteBuffer raw    = MemoryUtil.memAlloc(bytes.length);
                    raw.put(bytes).flip();

                    ByteBuffer pixels = STBImage.stbi_load_from_memory(raw, w, h, channels, 4);
                    MemoryUtil.memFree(raw);

                    if (pixels != null) {
                        GLFWImage.Buffer imageBuf = GLFWImage.malloc(1, stack);
                        imageBuf.position(0).width(w.get(0)).height(h.get(0)).pixels(pixels);
                        GLFW.glfwSetWindowIcon(handle, imageBuf);
                        STBImage.stbi_image_free(pixels);
                        LOGGER.info("[StrClient] Window icon set successfully.");
                    } else {
                        LOGGER.warn("[StrClient] Failed to decode icon: {}", STBImage.stbi_failure_reason());
                    }
                } else {
                    LOGGER.warn("[StrClient] Icon resource not found at {}", ICON_PATH);
                }
            } catch (Exception e) {
                LOGGER.warn("[StrClient] Failed to configure window icon: {}", e.getMessage());
            }

            // 🎯 按照 1.txt 要求加入的核心恢复补丁：
            // Re-trigger onEnable for all modules that were restored as enabled.
            // This ensures event listeners are registered properly even on subsequent launches.
            for (AbstractModule module : moduleManager.getAll()) {
                if (module.isEnabled()) {
                    module.onEnable(mc);
                }
            }
            LOGGER.info("[StrClient] Successfully re-triggered onEnable for active modules.");
        });

        // Core Client Tick Hook
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            GLFW.glfwSetWindowTitle(client.getWindow().getHandle(), WINDOW_TITLE);

            // 1. License Check Intercept
            if (!hasCheckedLicense && client.currentScreen instanceof TitleScreen) {
                hasCheckedLicense = true;

                if (!LicenseManager.checkSaved()) {
                    client.setScreen(new LicenseScreen(() -> {
                        if (client != null) {
                            client.setScreen(new TitleScreen(false));
                        }
                    }));
                }
            }

            // 2. Continuous Tick Processing and Key Binding Monitoring
            if (client.player != null) {
                moduleManager.tickAll(client);

                for (AbstractModule module : moduleManager.getAll()) {
                    KeyBinding kb = module.getKeyBinding();
                    if (kb != null && kb.wasPressed()) {
                        module.toggle(client);
                        settingsManager.save(moduleManager);
                    }
                }

                if (openMenuKey.wasPressed() && client.currentScreen == null) {
                    client.setScreen(new StrClientScreen(moduleManager, settingsManager));
                }

                if (hudEditKey.wasPressed() && client.currentScreen == null) {
                    client.setScreen(new HudEditScreen());
                }
            }
        });

        LOGGER.info("[StrClient] Ready — WebUI listening at http://localhost:5000");
    }

    public static StrClientMod getInstance() { return INSTANCE; }
    public ModuleManager getModuleManager()  { return moduleManager; }
    public SettingsManager getSettingsManager() { return settingsManager; }
}