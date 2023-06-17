# VCChecker

DiscordのVCに入っていないとサーバーに入れないようにするプラグインです。  

## 使い方

1. `config.yml`を編集します。
2. サーバーを起動します。

## 設定ファイル

設定ファイルは、`plugins/VCChecker/config.yml`にあります。

```yaml
# DiscordのBotトークン
token: <TOKEN>
# DiscordのサーバーID
guild_id: <GUILD_ID>
# DiscordSRVのアカウント連携jsonのURL
linked_accounts_url: <LINKED_ACCOUNTS_URL>
```

### linkedaccounts.json について
HUB鯖など、別のサーバーにDiscord SRVがインストールされていることを想定して、HTTP経由で連携アカウントを取得しています。  

`<LINKED_ACCOUNTS_URL>` には DiscordSRV で生成される linkedaccounts.json のURLを入れてください。  
[HostFilePlugin](https://github.com/Kamesuta/HostFilePlugin) などを使って、HTTPからアクセスできるようにしてください。
