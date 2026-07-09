package com.strongspy.strclient.modules.musicplayer;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MusicPlayerModule extends AbstractModule {

    private static final String MUSIC_DIR = "strclient_music";
    private final List<String> playlist = new ArrayList<>();
    private int currentIndex = -1;
    private Clip currentClip;
    private boolean isPaused = false;
    private float volume = 0.8f;
    private String playMode = "SEQUENTIAL";
    private String hudPosition = "TOP_LEFT";
    private boolean isPlaying = false;
    private String currentSongName = "";
    private static boolean commandsRegistered = false;
    private boolean hudRegistered = false;
    private boolean stopRequested = false;

    public MusicPlayerModule() {
        super("musicplayer", "Music Player", Category.UTILITY,
                "Play WAV music with progress and multiple modes.");
        registerCommands();
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofString("playMode", "Play Mode",
                "SEQUENTIAL, SINGLE_LOOP, LIST_LOOP", "SEQUENTIAL",
                "SEQUENTIAL", "SINGLE_LOOP", "LIST_LOOP"));
        registerSetting(ModuleSetting.ofString("hudPosition", "HUD Position",
                "Screen position for progress display", "TOP_LEFT",
                "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT", "CENTER"));
    }

    @Override
    public void onEnable(MinecraftClient client) {
        if (!hudRegistered) {
            HudRenderCallback.EVENT.register(this::renderHud);
            hudRegistered = true;
        }
        playMode = getString("playMode");
        hudPosition = getString("hudPosition");
        scanMusicDirectory();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§aMusic Player enabled. Use /music help"), false);
        }
    }

    @Override
    public void onDisable(MinecraftClient client) {
        stopRequested = true;
        stopMusic();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§cMusic Player disabled."), false);
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        playMode = getString("playMode");
        hudPosition = getString("hudPosition");
    }

    private void registerCommands() {
        if (commandsRegistered) return;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("music")
                    .executes(ctx -> {
                        MinecraftClient client = ctx.getSource().getClient();
                        if (client.player != null) sendHelp(client);
                        return 1;
                    })
                    .then(ClientCommandManager.literal("list").executes(ctx -> {
                        MinecraftClient client = ctx.getSource().getClient();
                        if (client.player != null) listSongs(client);
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("play")
                            .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "name");
                                        MinecraftClient client = ctx.getSource().getClient();
                                        if (client.player != null) playSongByName(client, name);
                                        return 1;
                                    }))
                    )
                    .then(ClientCommandManager.literal("pause").executes(ctx -> {
                        pauseMusic();
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("resume").executes(ctx -> {
                        resumeMusic();
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("stop").executes(ctx -> {
                        stopRequested = true;
                        stopMusic();
                        MinecraftClient client = ctx.getSource().getClient();
                        if (client.player != null) {
                            client.player.sendMessage(Text.literal("§cMusic stopped."), false);
                        }
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("volume")
                            .then(ClientCommandManager.argument("vol", IntegerArgumentType.integer(0, 100))
                                    .executes(ctx -> {
                                        int vol = IntegerArgumentType.getInteger(ctx, "vol");
                                        setVolume(vol / 100.0f);
                                        MinecraftClient client = ctx.getSource().getClient();
                                        if (client.player != null) {
                                            client.player.sendMessage(Text.literal("§aVolume set to " + vol + "%"), false);
                                        }
                                        return 1;
                                    }))
                    )
                    .then(ClientCommandManager.literal("now").executes(ctx -> {
                        MinecraftClient client = ctx.getSource().getClient();
                        if (client.player != null) showNowPlaying(client);
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("help").executes(ctx -> {
                        MinecraftClient client = ctx.getSource().getClient();
                        if (client.player != null) sendHelp(client);
                        return 1;
                    }))
            );
        });
        commandsRegistered = true;
    }

    private void scanMusicDirectory() {
        playlist.clear();
        Path musicPath = Paths.get(MinecraftClient.getInstance().runDirectory.getAbsolutePath(), MUSIC_DIR);
        if (!Files.exists(musicPath)) {
            try {
                Files.createDirectories(musicPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        File dir = musicPath.toFile();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".wav"));
        if (files != null) {
            for (File f : files) {
                playlist.add(f.getName());
            }
            playlist.sort(String::compareToIgnoreCase);
        }
        if (currentIndex >= playlist.size()) currentIndex = -1;
    }

    private void listSongs(MinecraftClient client) {
        if (playlist.isEmpty()) {
            client.player.sendMessage(Text.literal("§cNo .wav files found in .minecraft/" + MUSIC_DIR), false);
            return;
        }
        client.player.sendMessage(Text.literal("§6=== Music Playlist ==="), false);
        for (int i = 0; i < playlist.size(); i++) {
            String marker = (i == currentIndex) ? " §a▶" : "";
            client.player.sendMessage(Text.literal("§7" + (i + 1) + ". " + playlist.get(i) + marker), false);
        }
    }

    private void playSongByName(MinecraftClient client, String name) {
        int index = -1;
        for (int i = 0; i < playlist.size(); i++) {
            if (playlist.get(i).equalsIgnoreCase(name)) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            for (int i = 0; i < playlist.size(); i++) {
                if (playlist.get(i).toLowerCase().contains(name.toLowerCase())) {
                    index = i;
                    break;
                }
            }
        }
        if (index == -1) {
            client.player.sendMessage(Text.literal("§cSong not found: " + name), false);
            return;
        }
        currentIndex = index;
        stopRequested = false; // 重置停止标志
        playCurrent(client);
    }

    private void playCurrent(MinecraftClient client) {
        if (currentIndex < 0 || currentIndex >= playlist.size()) return;
        if (stopRequested) return;
        String fileName = playlist.get(currentIndex);
        Path musicPath = Paths.get(MinecraftClient.getInstance().runDirectory.getAbsolutePath(), MUSIC_DIR, fileName);
        File file = musicPath.toFile();
        if (!file.exists()) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§cFile not found: " + fileName), false);
            }
            return;
        }
        stopMusic(); // 停止当前播放
        stopRequested = false; // 重置停止标志，允许新播放

        CompletableFuture.runAsync(() -> {
            try {
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
                AudioFormat format = audioStream.getFormat();
                if (!isPCM(format)) {
                    if (client.player != null) {
                        client.execute(() -> {
                            client.player.sendMessage(Text.literal("§cUnsupported audio format (only PCM WAV allowed)."), false);
                        });
                    }
                    return;
                }
                DataLine.Info info = new DataLine.Info(Clip.class, format);
                Clip clip = (Clip) AudioSystem.getLine(info);
                clip.open(audioStream);
                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float dB = (float) (Math.log(volume) / Math.log(10.0) * 20.0);
                gainControl.setValue(dB);

                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        if (stopRequested) return;
                        isPlaying = false;
                        currentClip = null;
                        if ("SINGLE_LOOP".equals(playMode)) {
                            if (client.player != null && !stopRequested) {
                                client.execute(() -> playCurrent(client));
                            }
                        } else if ("LIST_LOOP".equals(playMode)) {
                            currentIndex = (currentIndex + 1) % playlist.size();
                            if (client.player != null && !stopRequested) {
                                client.execute(() -> playCurrent(client));
                            }
                        } else {
                            if (currentIndex + 1 < playlist.size()) {
                                currentIndex++;
                                if (client.player != null && !stopRequested) {
                                    client.execute(() -> playCurrent(client));
                                }
                            } else {
                                if (client.player != null) {
                                    client.execute(() -> {
                                        client.player.sendMessage(Text.literal("§ePlaylist finished."), false);
                                        currentSongName = "";
                                    });
                                }
                            }
                        }
                    }
                });
                clip.start();
                currentClip = clip;
                isPlaying = true;
                isPaused = false;
                currentSongName = fileName;
                if (client.player != null) {
                    client.execute(() -> {
                        client.player.sendMessage(Text.literal("§aNow playing: " + fileName), false);
                    });
                }
            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
                if (client.player != null) {
                    client.execute(() -> {
                        client.player.sendMessage(Text.literal("§cFailed to play: " + e.getMessage()), false);
                    });
                }
                e.printStackTrace();
            }
        });
    }

    private boolean isPCM(AudioFormat format) {
        return format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED || format.getEncoding() == AudioFormat.Encoding.PCM_UNSIGNED;
    }

    private void pauseMusic() {
        if (currentClip != null && isPlaying && !isPaused) {
            currentClip.stop();
            isPaused = true;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§eMusic paused."), false);
            }
        }
    }

    private void resumeMusic() {
        if (currentClip != null && isPaused) {
            currentClip.start();
            isPaused = false;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§aMusic resumed."), false);
            }
        }
    }

    private void stopMusic() {
        stopRequested = true;
        if (currentClip != null) {
            currentClip.stop();
            currentClip.close();
            currentClip = null;
        }
        isPlaying = false;
        isPaused = false;
    }

    private void setVolume(float vol) {
        this.volume = Math.max(0.0f, Math.min(1.0f, vol));
        if (currentClip != null && currentClip.isOpen()) {
            try {
                FloatControl gainControl = (FloatControl) currentClip.getControl(FloatControl.Type.MASTER_GAIN);
                float dB = (float) (Math.log(volume) / Math.log(10.0) * 20.0);
                gainControl.setValue(dB);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void showNowPlaying(MinecraftClient client) {
        if (isPlaying && currentSongName != null && !currentSongName.isEmpty()) {
            String status = isPaused ? " (paused)" : " (playing)";
            String progress = getProgressString();
            client.player.sendMessage(Text.literal("§aNow playing: " + currentSongName + status + " §7" + progress + "  Volume: " + (int) (volume * 100) + "%"), false);
        } else {
            client.player.sendMessage(Text.literal("§cNo music playing."), false);
        }
    }

    private void sendHelp(MinecraftClient client) {
        client.player.sendMessage(Text.literal("§6=== Music Player Commands ==="), false);
        client.player.sendMessage(Text.literal("§7/music list §f- List all songs"), false);
        client.player.sendMessage(Text.literal("§7/music play <name> §f- Play a song (partial match)"), false);
        client.player.sendMessage(Text.literal("§7/music pause §f- Pause current song"), false);
        client.player.sendMessage(Text.literal("§7/music resume §f- Resume current song"), false);
        client.player.sendMessage(Text.literal("§7/music stop §f- Stop current song"), false);
        client.player.sendMessage(Text.literal("§7/music volume <0-100> §f- Set volume"), false);
        client.player.sendMessage(Text.literal("§7/music now §f- Show current song"), false);
        client.player.sendMessage(Text.literal("§7/music help §f- This help"), false);
        client.player.sendMessage(Text.literal("§7Play mode can be changed in WebUI (Utility -> Music Player)"), false);
    }

    private String getProgressString() {
        if (currentClip == null || !currentClip.isOpen()) return "--:--/--:--";
        long pos = currentClip.getMicrosecondPosition();
        long total = currentClip.getMicrosecondLength();
        if (total <= 0) return "--:--/--:--";
        return formatTime(pos / 1000000) + "/" + formatTime(total / 1000000);
    }

    private String formatTime(long seconds) {
        long mins = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    private void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;
        if (!isPlaying && currentSongName.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        String progress = getProgressString();
        String display = "♫ " + currentSongName + (isPaused ? " ⏸" : " ▶") + " " + progress;

        int x = 10, y = 10;
        int windowWidth = client.getWindow().getScaledWidth();
        int windowHeight = client.getWindow().getScaledHeight();
        int textWidth = textRenderer.getWidth(display);
        int lineHeight = textRenderer.fontHeight + 2;

        String pos = hudPosition;
        switch (pos) {
            case "TOP_RIGHT":
                x = windowWidth - textWidth - 10;
                y = 10;
                break;
            case "BOTTOM_LEFT":
                x = 10;
                y = windowHeight - lineHeight - 10;
                break;
            case "BOTTOM_RIGHT":
                x = windowWidth - textWidth - 10;
                y = windowHeight - lineHeight - 10;
                break;
            case "CENTER":
                x = (windowWidth - textWidth) / 2;
                y = (windowHeight - lineHeight) / 2;
                break;
            default: // TOP_LEFT
                x = 10;
                y = 10;
                break;
        }

        context.drawTextWithShadow(textRenderer, Text.literal(Formatting.GREEN + display), x, y, 0xFFFFFF);
    }
}