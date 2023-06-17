package com.kamesuta.vcchecker;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

public final class VCChecker extends JavaPlugin implements Listener, EventListener {
    /**
     * ロガー
     */
    public static Logger logger;

    /**
     * Discord JDA
     */
    private JDA jda;

    /**
     * DiscordのボイスチャンネルがあるサーバーのID
     */
    private String serverId;

    /**
     * リンクされたアカウント
     */
    private LinkedAccounts linkedAccounts;

    @Override
    public void onEnable() {
        // Plugin startup logic
        logger = getLogger();

        // コンフィグを生成
        saveDefaultConfig();

        // サーバーIDを取得
        serverId = getConfig().getString("guild_id");
        if (serverId == null || serverId.isEmpty()) {
            // ログに出力
            getLogger().warning("サーバーIDが設定されていません。");
            // プラグインを無効化
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // リンクされたアカウントを初期化
        String linkedAccountUrl = getConfig().getString("linked_accounts_url");
        if (linkedAccountUrl == null || linkedAccountUrl.isEmpty()) {
            // ログに出力
            getLogger().warning("リンクアカウントのURLが設定されていません。");
            // プラグインを無効化
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        // リンクされたアカウントを初期化
        linkedAccounts = new LinkedAccounts(linkedAccountUrl);

        // トークンを取得
        String token = getConfig().getString("token");
        // Discord JDAを初期化
        if (!setupJDA(token)) {
            // プラグインを無効化
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // イベントリスナーを登録
        getServer().getPluginManager().registerEvents(this, this);

        // 定期的にアカウントリンクを更新
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            // リンクされたアカウントを更新
            linkedAccounts.update();
        }, 0, 20 * 60);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    /**
     * Discord JDAを初期化します。
     *
     * @param token Discordのトークン
     */
    private boolean setupJDA(String token) {
        // トークンが設定されていない場合
        if (token == null || token.isEmpty()) {
            // ログに出力
            getLogger().warning("トークンが設定されていません。");
            return false;
        }

        // Discord JDAを初期化
        JDABuilder builder = JDABuilder.createDefault(token);
        // イベントリスナーを登録
        builder.addEventListeners(this);
        // ビルド
        jda = builder.build();

        // 成功
        return true;
    }

    // マイクラに入る時VCに入っているかチェック
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // バイパス権限持ちなら何もしない
        if (event.getPlayer().hasPermission("vcchecker.bypass")) {
            return;
        }

        // プレイヤーを取得
        Player player = event.getPlayer();
        // プレイヤーのDiscord UIDを取得
        String discordUid = linkedAccounts.linkedDiscords.get(player.getUniqueId());
        // プレイヤーがDiscordにリンクされていない場合
        if (discordUid == null) {
            // ログに出力
            getLogger().warning("プレイヤー " + player.getName() + " はDiscordにリンクされていません。");
            // そもそもDiscordにリンクされていない人は鯖に入れないのでここでは何もしない
            return;
        }

        // Discordサーバーを取得
        Guild guild = jda.getGuildById(serverId);
        if (guild == null) {
            // ログに出力
            getLogger().warning("Discordサーバーが見つかりません。");
            return;
        }

        // メンバーを取得
        Member member = guild.getMemberById(discordUid);
        if (member == null) {
            // VCに入っていないのでマイクラからキック
            player.kickPlayer(ChatColor.RED + "連携したDiscordアカウントでVCに接続してください。");
            return;
        }

        // VCの状態を取得
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null) {
            // ログに出力
            getLogger().warning("DiscordメンバーのVC状態が取得できません。");
            return;
        }

        // プレイヤーがVCに入っているかチェック
        if (voiceState.inAudioChannel()) {
            // VCに入っているので何もしない
            return;
        }

        // VCに入っていないのでマイクラからキック
        player.kickPlayer(ChatColor.RED + "連携したDiscordアカウントでVCに接続してください。");
    }

    // VCから抜けたらマイクラからキック
    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof GuildVoiceUpdateEvent) {
            GuildVoiceUpdateEvent e = (GuildVoiceUpdateEvent) event;
            // VCを抜けたのでマイクラからキック
            if (e.getChannelJoined() == null) {
                // VC抜けたユーザーのDiscord UIDを取得
                String discordUid = e.getEntity().getId();
                // VC抜けたユーザーのマイクラIDを取得
                linkedAccounts.linkedAccounts.get(discordUid).forEach(uuid -> {
                    // マイクラからキック
                    Player player = getServer().getPlayer(uuid);
                    // バイパス権限持ちなら何もしない
                    if (player != null && !player.hasPermission("vcchecker.bypass")) {
                        // 同期スレッドで実行
                        getServer().getScheduler().runTask(this, () -> {
                            // プレイヤーがオンラインならVCに入るようにメッセージを送信
                            if (player.isOnline()) {
                                player.kickPlayer(ChatColor.RED + "VCから抜けたためキックされました。\n連携したDiscordアカウントでVCに接続してください。");
                            }
                        });
                    }
                });
            }
        }
    }
}
