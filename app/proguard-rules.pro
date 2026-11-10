# Keep kotlinx.serialization generated serializers for the app's models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.clozed.code.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.clozed.code.** {
    kotlinx.serialization.KSerializer serializer(...);
}
