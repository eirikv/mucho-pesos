# Add project specific ProGuard rules here.
# Keep kotlinx.serialization generated serializers and DTOs
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class vige.land.muchopesos.**$$serializer { *; }
-keepclassmembers class vige.land.muchopesos.** {
    *** Companion;
}
-keepclasseswithmembers class vige.land.muchopesos.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit service interfaces
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
