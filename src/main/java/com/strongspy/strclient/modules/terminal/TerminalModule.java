package com.strongspy.strclient.modules.terminal;

import com.strongspy.strclient.core.AbstractModule;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

public class TerminalModule extends AbstractModule {

    private static final int MAX_LOG_LINES = 500;
    private final LinkedList<String> logLines = new LinkedList<>();
    private Appender appender;
    private AtomicBoolean isCapturing = new AtomicBoolean(false);
    private JFrame terminalWindow = null;
    private JTextArea logArea;
    private JTextField inputField;
    private static TerminalModule instance;
    private boolean headlessFailed = false;

    public TerminalModule() {
        super("terminal", "Terminal", Category.UTILITY,
                "Open an external terminal window with live logs and command execution.");
        instance = this;
        // 尝试强制禁用 headless
        System.setProperty("java.awt.headless", "false");
    }

    @Override
    public void onEnable(MinecraftClient client) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("term")
                    .executes(ctx -> {
                        if (!isEnabled()) {
                            ctx.getSource().getClient().player.sendMessage(Text.literal("§cTerminal module is disabled."), false);
                            return 0;
                        }
                        if (headlessFailed) {
                            ctx.getSource().getClient().player.sendMessage(Text.literal("§cCannot open window. Please add -Djava.awt.headless=false to JVM args."), false);
                            return 0;
                        }
                        openTerminalWindow();
                        return 1;
                    })
            );
        });

        if (!isCapturing.get()) {
            isCapturing.set(true);
            appender = new InGameAppender();
            appender.start();
            ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).addAppender(appender);
        }

        if (client.player != null) {
            client.player.sendMessage(Text.literal("§aTerminal enabled. Use /term to open external window."), false);
        }
    }

    @Override
    public void onDisable(MinecraftClient client) {
        if (isCapturing.get() && appender != null) {
            ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).removeAppender(appender);
            appender.stop();
            isCapturing.set(false);
            appender = null;
        }
        closeTerminalWindow();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§cTerminal disabled."), false);
        }
    }

    private void openTerminalWindow() {
        if (terminalWindow != null && terminalWindow.isVisible()) {
            terminalWindow.toFront();
            return;
        }

        try {
            // 再次确保 headless 为 false
            if (GraphicsEnvironment.isHeadless()) {
                headlessFailed = true;
                MinecraftClient.getInstance().player.sendMessage(Text.literal("§cHeadless environment detected. Cannot open window."), false);
                return;
            }

            terminalWindow = new JFrame("StrClient Terminal");
            terminalWindow.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            terminalWindow.setSize(800, 500);
            terminalWindow.setLocationRelativeTo(null);

            logArea = new JTextArea();
            logArea.setEditable(false);
            logArea.setBackground(Color.BLACK);
            logArea.setForeground(Color.WHITE);
            logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            JScrollPane scrollPane = new JScrollPane(logArea);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

            inputField = new JTextField();
            inputField.setFont(new Font("Monospaced", Font.PLAIN, 12));
            JButton sendButton = new JButton("Send");
            sendButton.addActionListener(this::sendCommand);
            inputField.addActionListener(this::sendCommand);

            JPanel inputPanel = new JPanel(new BorderLayout());
            inputPanel.add(inputField, BorderLayout.CENTER);
            inputPanel.add(sendButton, BorderLayout.EAST);

            terminalWindow.add(scrollPane, BorderLayout.CENTER);
            terminalWindow.add(inputPanel, BorderLayout.SOUTH);

            terminalWindow.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    terminalWindow.setVisible(false);
                }
            });

            synchronized (logLines) {
                for (String line : logLines) {
                    logArea.append(line + "\n");
                }
            }
            logArea.setCaretPosition(logArea.getDocument().getLength());

            terminalWindow.setVisible(true);
            headlessFailed = false;
        } catch (HeadlessException e) {
            headlessFailed = true;
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§cHeadless environment. Please add -Djava.awt.headless=false to JVM args."), false);
        } catch (Exception e) {
            headlessFailed = true;
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§cFailed to open window: " + e.getMessage()), false);
        }
    }

    private void closeTerminalWindow() {
        if (terminalWindow != null) {
            terminalWindow.dispose();
            terminalWindow = null;
            logArea = null;
            inputField = null;
        }
    }

    private void sendCommand(ActionEvent e) {
        String cmd = inputField.getText().trim();
        if (cmd.isEmpty()) return;
        inputField.setText("");

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) {
            appendLog("§cNot connected to server.");
            return;
        }

        if (cmd.startsWith("/")) {
            client.getNetworkHandler().sendCommand(cmd.substring(1));
        } else {
            client.getNetworkHandler().sendChatMessage(cmd);
        }
        appendLog("§7> " + cmd);
    }

    public void addLogLine(String line) {
        String clean = line.replaceAll("§[0-9a-fk-or]", "");
        synchronized (logLines) {
            logLines.add(clean);
            if (logLines.size() > MAX_LOG_LINES * 1.5) {
                while (logLines.size() > MAX_LOG_LINES) {
                    logLines.removeFirst();
                }
            }
        }
        if (logArea != null && terminalWindow != null && terminalWindow.isVisible()) {
            SwingUtilities.invokeLater(() -> {
                logArea.append(clean + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
                int lines = logArea.getLineCount();
                if (lines > MAX_LOG_LINES) {
                    try {
                        int remove = lines - MAX_LOG_LINES;
                        int end = logArea.getLineEndOffset(remove);
                        logArea.replaceRange("", 0, end);
                    } catch (Exception ex) {
                        // ignore
                    }
                }
            });
        }
    }

    private void appendLog(String text) {
        addLogLine(text);
    }

    private class InGameAppender extends AbstractAppender {

        protected InGameAppender() {
            super("ExternalTerminalAppender", null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            String message = event.getMessage().getFormattedMessage();
            if (message.contains("ExternalTerminalAppender")) return;
            String level = event.getLevel().toString();
            String colored = switch (level) {
                case "ERROR" -> "§c[ERROR] ";
                case "WARN" -> "§e[WARN] ";
                case "INFO" -> "§a[INFO] ";
                default -> "§7[" + level + "] ";
            };
            if (instance != null) {
                instance.addLogLine(colored + message);
            }
        }
    }
}