/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Services used by the Gradle daemon to interact with the client"

dependencies {
    api(projects.baseServices)
    api(projects.stdlibJavaExtensions)
    api(projects.serviceProvider)
    api(libs.jsr305)
    api(projects.time)
    api(projects.logging)
    api(projects.daemonProtocol)
    api(projects.coreApi)
    api(projects.core)

    implementation(libs.commonsLang)
    implementation(projects.functional)
    implementation(projects.loggingApi)
    implementation(projects.modelCore)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
