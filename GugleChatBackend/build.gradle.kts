plugins {
    java
    id("org.springframework.boot") version "3.5.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("io.freefair.lombok") version "9.2.0"
}

group = project.property("maven_group") as String
version = project.property("project_version") as String

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-devtools")

    // Security for messaging
    implementation("org.springframework.security:spring-security-messaging")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // Hutool
    implementation("cn.hutool:hutool-all:5.8.46")

    // 代码生成器
    implementation("com.baomidou:mybatis-plus-generator:3.5.9")

    // 代码生成器模板引擎
    implementation("org.apache.velocity:velocity-engine-core:2.4.1")

    // 数据库查询工具
    implementation ("com.baomidou:mybatis-plus-spring-boot3-starter:3.5.16")
    implementation("com.baomidou:mybatis-plus-extension:3.5.16")
    implementation("com.baomidou:mybatis-plus-jsqlparser:3.5.16")

    // 测试
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

lombok {
    version = "1.18.46"
}
