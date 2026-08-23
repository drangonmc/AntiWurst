# AntiWurst

AntiWurst 是面向 Minecraft 1.20.1 Fabric 服务器的双端反作弊模组。2.0 版本新增登录阶段强制握手、持续客户端心跳，以及由服务端权威执行的移动和战斗行为检测。

## 功能

- **强制安装客户端模组**：服务端在登录完成前发送随机挑战。未安装 AntiWurst、拒绝查询、协议不兼容、响应损坏或本地扫描发现已知 Wurst 特征的客户端会被直接拒绝。
- **防重放与持续校验**：每次登录使用新的随机 nonce；进入游戏后还会周期性进行随机 heartbeat，超时或伪造响应会被踢出。
- **多源客户端扫描**：检查 Fabric 模组 ID、名称、描述、作者和多个已知 Wurst 类路径。检测过程不会上传模组列表或本地文件路径，只向服务器报告是否通过及首条原因。
- **服务端行为检测**：检测异常水平移动、悬空/飞行、异常竖直移动、超距离攻击、背后攻击、隔墙攻击和异常攻击频率。
- **证据聚合与衰减**：弱信号必须连续出现才会计分；旧证据会随时间衰减，累计达到阈值才踢出玩家。
- **误报保护**：创造/旁观、允许飞行、载具、鞘翅、水中、攀爬、漂浮/缓降效果、高速击退、传送和冰面移动等情况会被豁免或重置。

## 安装

要求：

- Minecraft `1.20.1`
- Fabric Loader `0.14.22` 或更高兼容版本
- Fabric API `0.88.1+1.20.1`
- Java 17

将同一个 `antiwurst-2.0.0.jar` 放入服务器和所有允许加入的客户端的 `mods` 目录。默认配置下，只在服务器安装而未在客户端安装的玩家会在登录阶段看到清晰的缺失模组提示，且无法进入世界。

> 如果服务器只希望使用行为检测而不强制客户端安装，可在配置中设置 `require-client-mod=false`。这不是默认值。

## 服务端配置

首次加载会创建 `config/antiwurst-server.properties`：

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `require-client-mod` | `true` | 强制登录握手与游戏内 heartbeat |
| `heartbeat-interval-ticks` | `200` | 两次 heartbeat 之间的 tick 数 |
| `heartbeat-timeout-ticks` | `100` | heartbeat 响应期限 |
| `violation-kick-score` | `10.0` | 踢出玩家的累计证据阈值 |
| `violation-decay-per-tick` | `0.015` | 每 tick 衰减的证据分数 |
| `maximum-attack-reach` | `4.25` | 服务端允许的最大攻击距离（格） |
| `maximum-attacks-per-second` | `22` | 一秒滑动窗口内的最大攻击数 |
| `cancel-suspicious-attacks` | `true` | 阻止已连续确认的异常攻击 |
| `log-violations` | `true` | 在服务端日志记录检测类型、测量值和分数 |

修改配置后重启服务器。阈值均会被限制在安全范围内，非法配置值会回退为默认值。

## 构建与验证

Windows：

```powershell
.\gradlew.bat clean build
```

Linux/macOS：

```bash
./gradlew clean build
```

`build` 会自动运行零外部测试框架的证据累计器验证。成品位于 `build/libs/antiwurst-2.0.0.jar`。

## 安全边界

登录 nonce 能阻止简单重放，heartbeat 能发现缺少持续协议支持的客户端，服务端行为检测也无法由客户端直接关闭。但玩家完全控制自己的客户端，因此任何 Fabric 客户端握手都无法在纯软件层面证明 JAR 从未被修改；有能力编写定制协议仿冒器的攻击者仍可能伪造“已安装”响应。AntiWurst 的核心防线因此是服务端行为证据，而不是盲目信任客户端自报结果。

该项目不会扫描操作系统进程、注册表、下载目录或其他隐私数据。

## License

见 [LICENSE](LICENSE)。
