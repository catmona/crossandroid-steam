# Keep JavaScript interface methods - required for WebView JS bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep line numbers for better stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep WebViewClient implementations
-keep class de.radicalfishgames.crosscode.GameWebViewClient { *; }

# Keep AboutLibraries classes (required by the library)
-keep class com.mikepenz.aboutlibraries.** { *; }

# Keep Material Design R class attributes (required by AboutLibraries)
-keep class com.google.android.material.R$attr { *; }

# JavaSteam finds a lot by reflection: ClientMsgProtobuf calls the static newBuilder() of each
# protobuf message class, MsgBase uses getDeclaredConstructor(), SteamUnifiedMessages looks up
# service methods by name, and KeyValue, CryptoHelper and HardwareUtils do the same with their own
# classes. The generated messages live in in.dragonbra.javasteam.protobufs, so keep all of it.
-keep class in.dragonbra.javasteam.** { *; }
-keep interface in.dragonbra.javasteam.** { *; }
-keep enum in.dragonbra.javasteam.** { *; }

# protobuf-java 4.x: generated classes extend GeneratedMessage (GeneratedMessageV3 is the 3.x name,
# still shipped for compatibility). Its runtime reflects on the generated fields and methods too.
-keep class com.google.protobuf.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessage { *; }
-keep class * extends com.google.protobuf.GeneratedMessageV3 { *; }
-keep class * extends com.google.protobuf.GeneratedMessage$Builder { *; }
-keep class * implements com.google.protobuf.ProtocolMessageEnum { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-dontwarn com.google.protobuf.**
