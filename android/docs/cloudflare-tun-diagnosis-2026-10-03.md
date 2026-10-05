# Cloudflare 530 / 1033 与 Clash TUN 排查

## 目标和当前状态

- 用户要求电脑启用 Clash TUN、手机启用 VPN 时，Android App 仍能通过 Cloudflare 隧道使用。
- 2026-10-03 19:20–19:29 排查了运行中的网关、连接器、电脑 DNS 和 USB 手机网络。
- 排查结束时用户选择“先不重启，只保留排查结果”；随后授权仅修改 DNS 配置，并由用户自己重启 Clash 与 mgy。
- 19:40 已将两项 DNS 例外写入电脑 Clash Verge 的持久配置；没有执行重载或重启，没有修改 Android 或 Go 源码。
- 当前待用户重启及验收，不能以文件写入或进程存在判断隧道已恢复。

以上是 19:40 配置写入时的状态。用户随后自行重启 Clash 和 mgy 并反馈恢复，19:48 又报告断线；最新复查见下一节。

## 19:48 第二次断线：代理节点连接超时

- 电脑 DNS 已返回 CF 的真实 `198.41.192.*`、`198.41.200.*` 地址，两项 DNS 例外存在于实际运行配置中。
- 新截图在 19:48:53 报告真实地址的 TLS EOF；电脑 Mihomo 在同一秒记录 region1/region2 隧道域名的 7844 请求匹配 `RuleSet/geolocation-!cn`，连接所选代理节点超时。四条请求均有此类记录。
- 只读控制器采样和保持数秒的诊断握手确认，CF 目标的实际路由属于 URLTest 自动选择组。代理日志中先后有不同节点连接失败；未记录完整切换事件，因此不声称“自动测速切换”就是触发断线的唯一原因。
- 初次复查本地 mgy 为 200、公网 530 / 1033、连接器 `/ready` 为 503、健康连接数 0。进程存在不能说明连接有效。
- 后续在没有配置改动和重启的情况下，cloudflared 自行恢复四条已注册连接，`/ready` 和公网健康检查均返回 200。20:03:04–20:03:27 连续五次复查结果一致，短样本尚不能证明长期稳定。
- 在电脑 TUN 保持开启时，用单个诊断套接字指定物理 WLAN 接口测试四个 CF 边缘地址：两个 TCP/TLS 握手成功，另两个发生连接或握手超时。同一批默认 TUN 路径诊断此时全部握手成功。没有关闭 TUN、修改系统路由或发送凭据；握手读取证书的测试仍不等于完整隧道验收。
- 不据此盲目把全部 CF 流量强制 DIRECT；没有修改代理规则、代理节点、Android 或 Go 源码，也没有重启服务。原 DNS 例外仍然需要保留。

这次故障涉及电脑代理到 CF 的连接路径；短时间恢复和 DNS 修复都不能保证上游代理节点始终可用。后续如需要变更路由，应先验证候选路径的实际隧道连接与持续性，再确定配置，不能只根据测速图标或一次握手选择固定节点。

## 已验证的证据

| 检查 | 结果 |
| --- | --- |
| 电脑本地网关 `/healthz` | HTTP 200，`status=ok` |
| 同一网关公布的公网 CF 地址 `/healthz` | HTTP 530，正文 `error code: 1033` |
| cloudflared 本地 `/ready` | HTTP 503 |
| cloudflared 指标 `cloudflared_tunnel_ha_connections` | 0 |
| 电脑 Clash | Mihomo v1.19.29，TUN 启用，DNS 为 fake-ip，网段 198.18.0.1/16 |
| 手机 | 移动数据 + Clash VPN，VPN UID 范围包括 App 的 UID 10512 |
| 系统 DNS 查询 `region1.v2.argotunnel.com` | 198.18.0.159 |
| 系统 DNS 查询 `region2.v2.argotunnel.com` | 198.18.0.216 |
| cloudflared 报错中使用的旧地址 | 198.18.0.150、198.18.0.151 |
| 旧地址 TCP 7844 / TLS 对照检查 | 两者均发生 TLS EOF，与截图一致 |
| 当前 DNS 返回的新地址、公共 DNS 返回的真实 CF 地址 | 均能完成 TCP 7844 / TLS 握手并返回 CF Origin CA 证书 |

握手检查不发送配对凭据或应用数据。CF 隧道使用专用 CA；诊断时读取证书的探针没有使用系统 CA 校验，不代表网关禁用 TLS 校验，也没有改动任何应用安全配置。TCP/TLS 探针仅证明能够到达边缘，不能代替完整隧道注册与 App 验收。

## 结论与边界

本次已独立复现电脑到 CF 的隧道连接失败。即使手机能够到达 CF，也会收到 530 / 1033。手机开着 Clash 不能单独解释电脑公网探针和连接器健康检查同时失败。

证据指向：cloudflared 仍使用旧的 Clash Fake-IP 地址，而这组旧地址已无法完成握手；当前重新查询得到的地址可以连接。映射变化发生的具体事件和时间没有确认，不推断是用户切换了哪项设置。

cloudflared 源码在初始发现阶段建立边缘地址池，重试从池中选择地址；单纯等待不能确保取得 Clash 当前的映射。此次没有执行重连，因此修复方案仍待实际恢复验证。

## 已写入持久文件、待用户重启生效的配置

电脑 Clash Verge 已启用独立 DNS 设置，持久文件为 `%APPDATA%/io.github.clash-verge-rev.clash-verge-rev/dns_config.yaml`。在现有 `dns.fake-ip-filter` 列表增加：

```yaml
- +.argotunnel.com
- +.cftunnel.com
```

两项规则只让 CF 隧道域名返回真实地址，不关闭 TUN、不关闭手机 VPN、不把其他域名改为直连。保留现有 DNS 和代理路由规则。

- 持久文件已经追加这两项规则；生成的运行文件没有直接修改。原文件的逐字节备份是忽略目录 `android/design/.verification/cf-tun-repair/dns_config.before-apply-20261003.yaml`，候选与检查结果也只保存在该目录。
- 写入前使用本机现有 `verge-mihomo.exe -t` 校验合并后的候选运行配置，返回 `test is successful`；写入后验证撤掉新增两行即可逐字节还原原文件。
- 用户操作顺序：完全退出电脑 Clash Verge，再重新打开并保持 TUN 启用；随后重启 mgy，释放旧边缘地址池。没有替用户执行这些操作。
- 19:40 写入后确认 mgy、cloudflared、Mihomo 的 PID 和启动时间均与写入前一致。
- 网关会从持久化认证文件读取已配对设备，从 `cf_tunnel.json` 读取原隧道身份；不删除这两类文件，不重置配对和域名。

## 后续验收条件

1. 电脑 TUN、手机 VPN 均保持开启。
2. 系统 DNS 对 CF 隧道域名返回真实地址，而不是 198.18.0.0/16 内地址。
3. 连接器 `/ready` 返回 200、健康连接数大于 0，原公网域名 `/healthz` 返回 200。
4. 手机无需重新配对，实际刷新历史并建立实时 WebSocket 连接。
5. 后续重新加载电脑代理配置后再次检查，确认不会因 Fake-IP 映射变化复发。不要未经用户允许切换其 VPN、TUN 或重启服务。

只读复查脚本在忽略目录：`python android/design/.verification/cf-health-check.py`。本轮输出为本地 200、公网 530 / 1033，`gateway_ok=true`、`tunnel_ok=false`。它不修改配置，也不发送凭据。

## 来源

- [Cloudflare 1033 说明](https://developers.cloudflare.com/support/troubleshooting/http-status-codes/cloudflare-1xxx-errors/error-1033/)：CF 找不到健康的 cloudflared 连接器。
- [Mihomo DNS 配置](https://wiki.metacubex.one/config/dns/)：Fake-IP 网段和过滤规则。
- [cloudflared 边缘地址池源码](https://github.com/cloudflare/cloudflared/blob/master/edgediscovery/edgediscovery.go)：初始发现和地址池重试。

## 2026-10-05：睡眠唤醒后530与未转局域网

用户吃饭回来，真机请求报530。既有只读脚本`python android/design/.verification/cf-health-check.py`两次复现：本地200、公网530且正文1033，gateway_ok=true、tunnel_ok=false；连接器20241/ready为503、readyConnections=0。电脑独立公网请求也失败，故已定位到电脑与Cloudflare之间的隧道连接，不需要以手机VPN作为唯一解释。

Windows System日志Kernel-Power事件42显示18:11:00进入睡眠，原因Application API；Power-Troubleshooter事件1记录18:33:24.580唤醒。WLAN日志8001于18:33:28.688记录重新连接。网关日志在18:33:25起记录四条CF连接断开；Mihomo在18:33:35报自动检测网络接口为空及interface not found。时间线支持睡眠断网、唤醒后隧道重连延迟是本次触发过程；没有证据指认调用睡眠API的具体应用，也未精确证明后续每次重试失败的内部原因。

DNS返回198.41.192.*与198.41.200.*真实地址，持久/运行配置仍保留argotunnel.com、cftunnel.com例外；此次不是已确认的Fake-IP旧映射问题。Mihomo只读控制器采样确认7844连接走geolocation-!cn代理链。没有切节点、关TUN、改路由或重启服务。连接器自行恢复4条连接；18:43:56、18:44:01、18:44:06、18:44:12四次本地/公网/ready均200、连接数4，记录位于忽略目录`android/design/.verification/cf-recovery-20261005.json`。mgy35724、cloudflared19856、Mihomo2412及各启动时间均保持不变。短时间恢复不能证明长期稳定，未替用户验证真实业务发送。

用户随后指出手机与电脑同网络，应能选择LAN。只读诊断确认：手机192.168.101.107/24、电脑192.168.101.120/24；手机ADB shell经nc请求`http://192.168.101.120:58900/healthz`返回200，说明该路径当时可达，不等同于已验证App UID下全部VPN行为。只读取`agy_standard_prefs.xml`中的路由键，发现LAN、自定义、IPv6、relay为空，active与primaryCloud均为原公网地址；没有读取加密设备凭据。后端本地`GET /api/v1/auth/endpoints`返回cloudflare和正确lan地址，表明已有接口能提供候选。

客户端缺口与证据：

- `ApiClient.pair()`已解析配对响应中的候选，但customUrl分支仅调用updateEndpoints(custom, active)。updateEndpoints会先清除所有路由槽，LAN因此不能保存；普通分支才保存LAN等字段。当前手机LAN确实为空，但没有当次配对历史日志，不断言本次必然由这个分支造成。
- Android没有调用`/api/v1/auth/endpoints`的配对后刷新流程。ConnectionManager只能探测本地已有候选，无法补取缺失或过期的LAN地址；当前Wi-Fi下也无法选择空候选。
- RouteFailoverInterceptor只处理IOException，并尝试primaryCloud。HTTP530是正常返回的错误响应，没有进入异常分支，也不会主动从公网转LAN。网络回调触发探测时仍然缺少LAN候选。

修复方向是复用现有候选接口与选路器：配对时保留完整可信候选，已配对启动/网络变化时刷新候选，Wi-Fi下优先选择可达LAN；公网失败时触发重新探测与后续请求切换。写操作不能仅凭5xx跨地址盲目重发，以免重复发送/回退。此轮只定位并提出方案，没有修改选路代码、设备配对、VPN或电脑睡眠设置。

用户随后确认手机已成功连接，并要求继续排查。为验证代码路径而非仅看源码，临时在既有ChatUxTest中加入隔离配对探针，使用独立偏好与FakeGateway，仅在emulator-5554运行`am instrument -w -e class com.antigravity.mobile.ui.demo.ChatUxTest#diagnosticCustomPairMustRetainKnownLanCandidate com.antigravity.mobile.test/androidx.test.runner.AndroidJUnitRunner`。输入明确包含LAN候选，调用真实ApiClient.pair(info, customUrl)，配对成功；随后断言应保存LAN，实际得到null，测试3.373秒明确失败（lan-custom-pair-baseline.log）。这证明自定义分支确实丢掉已知候选，仍不能还原手机当次配对历史。临时测试已从源码撤回，片段lan-custom-pair-probe.kt与日志仅保留忽略目录作为待修复依据，不将未修复的失败测试提交为正常测试。没有重新配对真机或操作真实网关的配对接口。
