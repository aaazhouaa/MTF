# kotlinx.serialization 序列化模型：运行时按类名/字段名做 JSON 编解码，
# 混淆后字段名变化会导致反序列化丢字段甚至运行时崩溃。
# data.model 是模型集中地，整包保留。
-keep class com.aazhou.mtforum.data.model.** { *; }

# 其余散落的 @Serializable 类（表情包 SmileyPack/Smiley、缓存 CacheEntry、
# 日志 LogEntry、各 ViewModel 内的私有缓存类）按注解统一保留。
-keep @kotlinx.serialization.Serializable class * { *; }
