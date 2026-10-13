# MTF

MTF 是一款 [MT 论坛](https://bbs.binmt.cc/)（bbs.binmt.cc）的第三方 Android 客户端，使用 Kotlin 与 Jetpack Compose 从零构建。代码位于本仓库的 [`MTF/`](MTF/) 目录。

## 功能特性

- **首页**：热帖排行、卡片流资讯，双击顶栏一键回到顶部。
- **板块与列表**：版块分组浏览、版块内主题列表、导读，均支持触底自动加载更多。
- **帖子详情**：
  - 楼中楼（嵌套回复）与引用卡渲染；
  - 灌水评论自动折叠、只看楼主、正序 / 倒序切换；
  - 正文图片单图 / 多图网格自适应排布，评论楼层定位。
- **互动**：回复、引用回复、点赞、收藏、关注（含备注名）。
- **发布**：发帖、编辑帖子 / 评论，草稿箱；支持表情、贴图上传、`@` 提及。
- **私聊**：一对一私信收发，聊天记录本地缓存。
- **消息提醒**：我的帖子 / 坛友互动 / 系统提醒 / 应用提醒 / 私信，分类未读圆点。
- **搜索**：帖子、圈子、用户统一搜索。
- **下载与离线**：帖子导出为 HTML（图片内嵌），或保存为应用内存档离线查看。
- **个性化**：6 套主题配色、深色模式、液态玻璃底栏。
- **其它**：签到、我的帖子、我的收藏、好友与关注列表、本地日志与崩溃日志。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 语言 | Kotlin 2.2 |
| UI | Jetpack Compose（BOM 2025.12.01）+ Material 3 |
| 网络 | OkHttp 4.12 |
| HTML 解析 | Jsoup |
| 图片 | Coil 2.7 |
| 视频 | Media3 ExoPlayer |
| 导航 / 状态 / 持久化 | Navigation Compose、ViewModel、DataStore、WorkManager |
| 序列化 | kotlinx.serialization |
| 依赖注入 | 手动 DI（`AppContainer`） |

## 构建

环境要求：

- JDK 21
- Android SDK（`compileSdk 36`、`buildTools 36.0.0`）
- Gradle 8.14.2（随项目 `MTF/gradlew` 提供）

```bash
cd MTF
# 构建 Release APK
./gradlew :app:assembleRelease
```

构建产物为 `MTF/app/build/outputs/apk/release/MTF-v<version>.apk`。

> Release 签名：若仓库外的 `.secrets/mtf/keystore.properties` 存在则使用正式签名，否则回退为 debug 签名，便于直接安装测试。

## 底层机制

- **`ContentCache` 磁盘快照缓存**：TTL 过期 + 128MB 容量上限淘汰（先清过期、再按最旧删除），写入走「临时文件 + rename」原子替换，坏文件静默降级为「无缓存」走网络路径。
- **`RateLimiter` 请求节流**：保证两次请求间隔不小于 400ms；命中 514 / 403 / 429 时退避 3 秒，静态资源可过滤放行。
- **`WafSolver` WAF 本地求解**：在原生 HTTP 层复现站点阿里云 ESA 的 `acw_sc__v2` JS 挑战算法（40 项置换表 + XOR），无需 WebView；失败时降级到 WebView 真人验证兜底。
- **`NoticePollWorker` 后台轮询**：论坛无推送服务端，用 WorkManager 每 15 分钟拉一次提醒，来新消息发系统通知；进应用时额外即时查一次。

## 项目结构

```
MTF/app/src/main/java/com/aazhou/mtforum/
├── data/                  # 数据层
│   ├── cache/             # 磁盘快照缓存
│   ├── download/          # 帖子下载与离线存档
│   ├── export/            # HTML 导出
│   ├── log/               # 本地日志
│   ├── model/             # 数据模型
│   ├── network/           # HTTP 客户端、Cookie、WAF 求解
│   ├── parser/            # 站点 HTML 解析
│   └── repo/              # 各业务仓库
├── image/                 # 图片加载
└── ui/                    # 界面层
    ├── common/            # 共享组件
    ├── glass/             # 液态玻璃底栏
    ├── nav/               # 路由
    ├── screen/            # 各页面
    └── theme/             # 主题
```

## 测试

```bash
cd MTF
./gradlew :app:testReleaseUnitTest
```

单元测试覆盖站点 HTML 解析、正文渲染、WAF 求解、灌水折叠、贴图上传与草稿箱等核心逻辑。

## 免责声明

本项目为第三方客户端，与 MT 论坛及其运营方无任何关联，仅用于学习交流。请勿将其用于任何违反站点规则或相关法律法规的用途，使用本项目产生的一切后果由使用者自行承担。
