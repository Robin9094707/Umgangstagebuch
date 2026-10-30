# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class app.umgangstagebuch.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.umgangstagebuch.**$$serializer { *; }
-keepclasseswithmembers class app.umgangstagebuch.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class app.umgangstagebuch.data.model.** { *; }
