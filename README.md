## 集成
#### Step 1. Add the JitPack repository to your build file，Add it in your root build.gradle at the end of repositories:
   ```
   allprojects {
       repositories {
          ...
          maven { url 'https://jitpack.io' }
       }
   }
   ```
#### Step 2. Add the dependency
  ```
  dependencies {
       implementation 'com.github.siren4:RxBus:2.0.0'
  }
  ```
## 文档
#### https://github.com/siren4/RxBus/wiki