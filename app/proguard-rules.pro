# kotlinx.serialization：保留序列化器与被 @Serializable 标注类型的元数据。
# 官方依赖自带 consumer rules，这里补充项目模型类，避免 R8 全量优化后反射查找失败。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.vela.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.vela.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
