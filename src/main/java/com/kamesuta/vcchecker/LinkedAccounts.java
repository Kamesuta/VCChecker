package com.kamesuta.vcchecker;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Streams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class LinkedAccounts {
    /**
     * プレイヤーとDiscord UIDのリンクを保持するマップ
     */
    private final String linkedAccountsUrl;

    /**
     * Gson
     */
    private final Gson gson = (new GsonBuilder()).setPrettyPrinting().create();

    /**
     * プレイヤーとDiscord UIDのリンクを保持するマップ
     */
    public ListMultimap<String, UUID> linkedAccounts = Multimaps.newListMultimap(new HashMap<>(), ArrayList::new);
    /**
     * Discord UIDとプレイヤーのリンクを保持するマップ
     */
    public HashMap<UUID, String> linkedDiscords = new HashMap<>();

    public LinkedAccounts(String linkedAccountsUrl) {
        this.linkedAccountsUrl = linkedAccountsUrl;
    }

    /**
     * プレイヤーとDiscord UIDのリンクを保持するマップを取得します。
     */
    public void update() {
        // HttpClient生成
        HttpClient cli = HttpClient.newHttpClient();
        // HttpRequest生成
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(linkedAccountsUrl))
                .GET()
                .build();
        // コンテンツ
        String fileContent;
        // リクエスト送信
        try {
            // HttpResponse取得
            HttpResponse<String> res = cli.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                VCChecker.logger.warning("linkedaccounts.jsonの取得に失敗しました: ステータスコード" + res.statusCode());
                return;
            }
            fileContent = res.body();
        } catch (Exception e) {
            VCChecker.logger.log(Level.WARNING, "linkedaccounts.jsonの取得に失敗しました。", e);
            return;
        }

        // ファイルチェック
        if (fileContent == null || fileContent.isEmpty()) {
            VCChecker.logger.warning("linkedaccounts.jsonの取得に失敗しました: ファイルが空です。");
            return;
        }
        // JSONを読み込み
        JsonObject jsonObject;
        try {
            jsonObject = gson.fromJson(fileContent, JsonObject.class);
        } catch (Throwable t) {
            VCChecker.logger.log(Level.WARNING, "linkedaccounts.jsonの読み込みに失敗しました。", t);
            return;
        }

        // 関係マップを作成
        ListMultimap<String, UUID> linkedAccounts = Multimaps.newListMultimap(new HashMap<>(), ArrayList::new);
        HashMap<UUID, String> linkedDiscords = new HashMap<>();

        jsonObject.entrySet().forEach(entry -> {
            String key = entry.getKey();
            if (key.isEmpty()) {
                // empty values are not allowed.
                return;
            }

            JsonElement entryValue = entry.getValue();
            List<String> values = !entryValue.isJsonArray()
                    ? Collections.singletonList(entryValue.getAsString())
                    : Streams.stream(entryValue.getAsJsonArray())
                    .map(JsonElement::getAsString)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            if (values.isEmpty()) {
                // empty values are not allowed.
                return;
            }

            try {
                values.forEach(value -> linkedAccounts.put(key, UUID.fromString(value)));
                values.forEach(value -> linkedDiscords.put(UUID.fromString(value), key));
            } catch (Exception e) {
                VCChecker.logger.log(Level.WARNING, "linkedaccounts.jsonの読み込みに失敗しました。", e);
            }
        });

        // 更新
        this.linkedAccounts = linkedAccounts;
        this.linkedDiscords = linkedDiscords;
    }
}
