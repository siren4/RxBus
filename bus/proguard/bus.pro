#bus
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.siren.liu.bus.annotation.Subscribe <methods>;
}
-keep enum com.siren.liu.bus.mode.ThreadMode { *; }
