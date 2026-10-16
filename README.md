# ntfy Android DSH

这是基于 [ntfy Android 上游项目](https://github.com/binwiederhier/ntfy-android) 修改的独立 fork，应用 ID 为 `io.heckel.ntfy.dsh`。它仍使用 ntfy 服务器和主题，并加入 DSH/VS Code Agent 会话聊天、全局自定义主题色及聊天背景/气泡颜色设置。

配套项目：[ntfy-task-notifier](https://github.com/mrxss192/ntfy-task-notifier)，提供 Windows bridge、PowerShell 安装器和 VS Code Agent 扩展。普通 ntfy 客户端也能向相同主题发送 `/vscode` 与 `/reply`；Android DSH fork 会将每条 VS Code 会话显示为独立聊天卡片，便于查看上下文和定向回复。

## 使用方法

1. 在 Windows 安装 `ntfy-task-notifier` 中的 DSH Agent bridge，生成手机命令主题和回复主题。
2. 在 Android 应用中打开 VS Code chats，填写 ntfy 服务器、命令主题和回复主题。
3. 在 VS Code 登录 Copilot，并使用 `--enable-proposed-api local-dsh.ntfy-vscode-agent` 启动；安装并启用 ntfy VS Code Agent 扩展。
4. 在手机聊天页发送 `/vscode <任务>` 创建 Copilot Chat；回复会话卡片，或发送 `/reply <短码> <消息>` 续写指定会话。

主题是远程任务入口凭据，请勿公开分享或通过 ntfy 发送密码、密钥及其他敏感内容。Autopilot 会绕过 Chat 工具审批，但仍受组织策略、工作区信任和操作系统沙箱限制。

## 下载与签名

发布包位于 [GitHub Releases](https://github.com/mrxss192/ntfy-android-dsh/releases)。本 fork 不使用上游 ntfy APK 的签名密钥；当前构建产物为 unsigned，Android 不能直接安装。签名状态会在每个 Release 中明确标注。

## Build
For up-to-date building instructions, please see the [official docs](https://docs.ntfy.sh/develop/#android-app).

## Translations
We're using [Weblate](https://hosted.weblate.org/projects/ntfy/) to translate the ntfy Android app. We'd love your participation.

<a href="https://hosted.weblate.org/engage/ntfy/">
<img src="https://hosted.weblate.org/widgets/ntfy/-/multi-blue.svg" alt="Translation status" />
</a>

## License
Made with ❤️ by [Philipp C. Heckel](https://heckel.io), distributed under the [Apache License 2.0](LICENSE).

Thank you to these fantastic resources:
* [RecyclerViewKotlin](https://github.com/android/views-widgets-samples/tree/main/RecyclerViewKotlin) (Apache 2.0)
* [Just another Hacker News Android client](https://github.com/manoamaro/another-hacker-news-client) (MIT)
* [Android Room with a View](https://github.com/googlecodelabs/android-room-with-a-view/tree/kotlin) (Apache 2.0)
* [Firebase Messaging Example](https://github.com/firebase/quickstart-android/blob/7147f60451b3eeaaa05fc31208ffb67e2df73c3c/messaging/app/src/main/java/com/google/firebase/quickstart/fcm/kotlin/MyFirebaseMessagingService.kt) (Apache 2.0)
* [Designing a logo with Inkscape](https://www.youtube.com/watch?v=r2Kv61cd2P4)
* [Foreground service](https://robertohuertas.com/2019/06/29/android_foreground_services/)
* [github/gemoji](https://github.com/github/gemoji) (MIT) for as data source for an up-to-date [emoji.json](https://raw.githubusercontent.com/github/gemoji/master/db/emoji.json) file
* [emoji-java](https://github.com/vdurmont/emoji-java) (MIT) has been stripped and inlined to use the emoji.json file
