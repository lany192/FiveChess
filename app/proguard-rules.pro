# R8 full mode（AGP 默认）已经完成了绝大部分混淆：类/方法/字段改名、所有类重打包到根包、
# 剥离 Kotlin Metadata、把源文件名替换成 r8-map-id 哈希。这里只补默认没做的部分。
#
# 改规则前先看实测结论：
# - -keepattributes !Signature 在 R8 full mode 下是空操作：只有被 keep 的类（库的反射入口）
#   才保留泛型签名，项目自己的类本来就没有签名元数据，加了产物也不会变。
# - 难度档位以枚举名持久化（data/settings/AiLevelStore.kt），R8 默认不改枚举字段名，
#   这里刻意不加 keep，避免关掉枚举优化。
# - 字符串常量由 buildSrc 的 EncryptStringsTask 在 R8 之前加密，与 keep 规则无关；
#   注入的解密类由该任务注入并被正常调用，同样不需要 keep。

# 行号无法去除：AGP 9.4 的 R8 对 -keepattributes !LineNumberTable 是空操作
# （实测规则已进入合并后的 configuration.txt，但 dex 里行号原样保留），
# 反编译后仍能与源码逐行对齐。这不是配置问题，加规则解决不了，所以不写这行。
# 若要彻底去除，只能在 R8 之后再做一遍 dex 改写——会同时废掉 mapping.txt 的行号信息，
# 让真机崩溃栈失去定位能力，不值得。
