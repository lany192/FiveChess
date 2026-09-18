plugins {
    `kotlin-dsl`
}

repositories {
    // 与 settings.gradle.kts 一致的国内镜像优先策略
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}

dependencies {
    // 只在构建期改写项目自己的字节码，不依赖 AGP 类型（AGP 接线写在 app/build.gradle.kts）
    implementation("org.ow2.asm:asm:9.10.1")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
