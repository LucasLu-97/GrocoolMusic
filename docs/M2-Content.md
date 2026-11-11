# M2 内容层第一批：领域模型、POST 签名与多屏幕 UI

> 目标：把 M1 的「搜索 → 取流 → 播放」单屏闭环，扩展成**内容与功能完全移植酷狗概念版**的内容层骨架。
> 本批次解决三件事：① POST 接口解锁 ② 统一领域模型与 Repository ③ 多标签内容 UI。
> 全部结论都经过**真实网关实测 + 本地 JVM 编译验证 + 本地 Android 编译验证**，不只是文档推断。

## 一、本批次最大的协议发现：POST body 必须参与签名

M0/M1 的三个接口（search / song_url / lyric）全是 GET，签名串末尾的 body 恒为空串。
内容层多数接口是 POST + JSON body，独立冒烟脚本对照实测（2026-11-11）：

| 请求 | 网关响应 |
| --- | --- |
| `rankAudio`，签名串含 `JSON.stringify(body)` | `status=1`，返回 5 首带 hash / 时长的真实歌曲 |
| `rankAudio`，签名串**不含** body（其余完全相同） | `status=0`、`error_code=20006`、0 首 |

**结论**：酷狗的 `signature = MD5(salt + 按 key 排序的 `key=value` 串 + body 字符串 + salt)`。
依据上游 MIT 源码 `util/request.js:139`（`JSON.stringify(options.data)`）与
`util/helper.js:61`（`signatureAndroidParams(params, data)`）。

实现要点（已落地 `kugou/`）：

- `RequestOptions.body: String`、`BuiltRequest.body: String`；`Content-Type: application/json` 仅在有 body 时注入。
- `DefaultHttpTransport` 用 `HttpURLConnection.doOutput` 写出 body，Android / JVM 都支持。
- `KugouContentApi.jsonBody()` 生成**紧凑且保序**的 JSON（对齐 JS `JSON.stringify` 的插入顺序），
  因为 body 是逐字节参与签名的，多一个空格或换一个字段顺序都会验签失败。
- `PostBodySignatureTest` 锁定该行为；`GET requests keep the M0 golden signature unchanged`
  确保新增能力没有破坏已验证的 M0 签名链路。

这一条解锁了 KuGouMusicApi 中大量 POST 接口（歌单详情、每日推荐、评论、歌手歌曲、云盘等），
是 M3/M4 继续铺功能的前提。

## 二、已实测可用的内容接口

| 接口 | 上游 module | 方法 | 实测结果 |
| --- | --- | --- | --- |
| `/ocean/v6/rank/list` | `rank_list.js` | GET | `status=1`，**56 个官方榜单**（TOP500 / 内地榜 / 飙升榜 …） |
| `/ocean/v6/rank/info` | `rank_info.js` | GET | `status=1`，榜单元信息 + 3 首预览曲（无 hash） |
| `/openapi/kmr/v2/rank/audio` | `rank_audio.js` | **POST** | `status=1`，分页歌曲列表，`audio_info.hash_128` 可取流 |
| `/ocean/v6/rank/vol` | `rank_vol.js` | GET | 往期榜单（已登记接口，解析宽松容错） |
| `/mobileservice/api/v5/rank/rec_rank_list` | `rank_top.js` | GET | 编辑推荐榜单（已登记） |
| `/api/v3/search/hot_tab` | `search_hot.js` | GET | `status=1`，**9 组热搜**（热搜榜 / 飙升榜 / DJ榜 …） |
| `/pubsongs/v2/get_other_list_file_nofilt` | `playlist_track_all.js` | GET | 空 `global_collection_id` 返回 `20010 参数错误`；需真实歌单 id（M2 后段接歌单广场） |

关键字段映射（榜单歌曲，来自真实响应）：

- `audio_info.hash_128` → 取流 hash（不是 `FileHash`，与搜索结果不同）
- `audio_info.duration_128` → 毫秒时长
- `album_info.sizable_cover` / `trans_param.union_cover` → 封面（含 `{size}` 占位符，已替换为 480 并升级 https）
- 榜单响应**没有** `Privilege` 字段，是否 VIP 以 `/v5/url` 的 `status=2` 为准

## 三、架构落地

```
UI 屏幕（Home / Explore / ChartDetail / Search / Library）
        ↓ 只消费 SongItem / ChartItem / ShelfItem
adapter/（隔离带：所有自有改动收拢于此）
        ↓
KugouRepository（纯 JVM，读状态 → 校验 → 解析 → 装配首页 Shelf）
        ↓
KugouContentParser（原始 JSON → 领域模型，全部容错）
        ↓
KugouApi + KugouContentApi（签名唯一底座；每个接口只写参数表）
        ↓
KugouModels（Song / Album / Artist / Playlist / Chart / Shelf / Comment）
```

- **领域模型** `KugouModels.kt`：禁止 UI 直接引用酷狗字段名。酷狗字段改名只改解析器。
- **Repository 统一状态校验**：`status != 1` 抛带错误码的异常，绝不再把验签/风控失败
  显示成「没有结果」（M1 真机 `mid` 为空的教训）。
- **首页装配** `homeFeed()`：BitChord 的 `home()` 写死 `Innertube.browse("FEmusic_home")`，
  酷狗没有等价物，必须由多个真实接口拼装。当前产出 3 个 `Shelf`：
  热搜关键词（12 个）、精选榜单（12 个）、TOP500 新歌速递（10 首）。
  单个接口失败**只跳过该货架**，不会让整页空白，并在 `HomeFeed.failures` 回报。
- **`adapter/` 隔离带**：同步 BitChord UI 时，冲突只可能发生在这一层。

## 四、真实网关验证（Kotlin 编译产物）

`gradle :kugou:gatewayProbe` —— 用**本仓库编译出的 Kotlin 代码**（而非独立 Python 脚本）
打真实网关，本地运行结果：

```
榜单列表 rankList (GET)      count=56 status=1
热搜 hotSearch (GET)         groups=9
榜单歌曲 rankAudio (POST)    chart=TOP500 count=5，带 hash / 时长 / 专辑
首页内容流 homeFeed          shelves=3 kinds=[KEYWORDS, CHARTS, SONGS] failures=[]
```

> 该任务**不参与 CI**（GitHub runner 位于境外，实测会触发 `errcode 20028 本次请求需要验证`）。
> CI 只跑不依赖网络的契约测试。

## 五、UI 第一批

- 四标签底部导航：**收听 / 浏览 / 搜索 / 资料库**，全局常驻迷你播放器。
- 收听：热搜词 chips（点击直接搜索）、榜单横向卡片、新歌速递列表，全部可点击播放。
- 浏览：56 个官方榜单 → 点击进入榜单详情（POST 分页歌曲，带排名）。
- 搜索：保留 M1 闭环，改用领域模型渲染。
- 资料库：**明确标注为 M3 占位**，不伪装成已实现功能。
- 封面暂用 hash 生成稳定色调 + 首字占位，**不引入图片加载库**，符合低资源目标；
  后续接 Coil 时只需改 `ui/Components.kt` 的 `CoverBox` 一个组件。

## 六、本地编译环境（本轮新增）

本机原先无 JDK / Gradle / Android SDK，所有编译反馈依赖 GitHub Actions 黑盒（每轮 3–5 分钟）。
本轮已在**用户级目录（仓库之外）**装好：

| 组件 | 路径 / 版本 | 下载线路 |
| --- | --- | --- |
| JDK | `C:\Users\Lucas.Lu\.workbuddy\tools\jdk-17`（OpenJDK 17） | 华为云 `repo.huaweicloud.com/openjdk`，≈780 KB/s |
| Gradle | `...\tools\gradle-8.10.2` | 华为云 `mirrors.huaweicloud.com/gradle`，4.5 MB/s |
| Android SDK | `...\tools\android-sdk`（platform 35 / build-tools 35.0.0 / platform-tools） | `dl.google.com`，5.4 MB/s |
| 国内 Maven 镜像 | `...\tools\gradle-init\aliyun-mirrors.init.gradle`（**Groovy**） | 阿里云，境外源 857 B/s → 镜像快一个数量级 |

**关键**：init script 只在本机用 `-I` 显式传入，`settings.gradle.kts` 未改动，
GitHub Actions 仍走官方源，CI 行为完全不变。

一键本地验证：

```bash
export JAVA_HOME="C:/Users/Lucas.Lu/.workbuddy/tools/jdk-17"
export ANDROID_HOME="C:/Users/Lucas.Lu/.workbuddy/tools/android-sdk"
gradle -I "C:/Users/Lucas.Lu/.workbuddy/tools/gradle-init/aliyun-mirrors.init.gradle" \
  :kugou:test :app:assembleDebug
```

实测：`:kugou:test` **23 个测试 21 秒全绿**；`:app:assembleDebug` **25 秒 BUILD SUCCESSFUL**。
Kotlin DSL 的 init script 无法解析 `RepositoryHandler`（Gradle 已知限制），必须用 Groovy 版。

另外给 debug 构建加了 `applicationIdSuffix = ".debug"`，本地调试包与统一签名的
release 包**并存**，避免调试包覆盖正式包、也不破坏覆盖安装链路。
> 小米真机对 USB 安装会弹「允许安装」确认（`INSTALL_FAILED_USER_RESTRICTED`），
> 需手机侧点一次；日常发版仍以统一签名的 release 覆盖安装为主。

## 七、完成标志与后续

- [x] POST body 签名解锁并实测
- [x] 7 个内容接口登记、其中 4 个实测可用
- [x] 领域模型 + Repository + adapter 隔离带落地
- [x] 4 标签 UI + 榜单详情 + 首页内容装配
- [x] 23 个契约测试、Kotlin 真实网关探针、本地 Android 编译
- [ ] 真机 UI 验收（等 0.3.0 release 覆盖安装）
- [ ] M2 后段：歌单广场 / 每日推荐 / 歌手与专辑详情 / 评论 / 封面加载 / 分页
- [ ] M3/M4：登录态、概念版免费听 Auth、人机验证流程、云盘
