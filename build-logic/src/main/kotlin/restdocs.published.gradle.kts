// 배포 모듈 공통: maven-publish 와 POM(이름·설명·라이선스·개발자·scm).
// 좌표와 설명은 모듈 build 파일의 publishedModule { } 에서 받는다. bom(java-platform)도 이 플러그인을 쓴다.
// gradle-plugin(별도 included build)의 POM 도 같은 값을 쓴다. 한쪽을 바꾸면 gradle-plugin/build.gradle.kts 도 바꾼다.
plugins {
    `maven-publish`
}

val publishedModule = extensions.create<PublishedModuleExtension>("publishedModule")
val projectUrl = providers.gradleProperty("projectUrl")
val scmConnection = projectUrl.map { "scm:git:$it.git" }

val publication = publishing.publications.create<MavenPublication>("maven") {
    pom {
        name.set(publishedModule.artifactId)
        description.set(publishedModule.description)
        url.set(projectUrl)
        inceptionYear.set("2024")
        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("devYSK")
                name.set("devYSK")
            }
        }

        scm {
            url.set(projectUrl)
            connection.set(scmConnection)
            developerConnection.set(scmConnection)
        }
    }
}

// MavenPublication.artifactId 는 지연 값(Provider)을 받지 않는다. 모듈 build 파일이 publishedModule 을 채운 뒤에 넣는다.
// 읽은 뒤에 바뀌면 jar 이름·POM 과 배포 좌표가 어긋나므로 먼저 값을 잠근다.
afterEvaluate {
    publishedModule.artifactId.disallowChanges()
    publication.artifactId = publishedModule.artifactId.orNull
        ?: throw GradleException("${project.path}: publishedModule { artifactId = ... } 가 없습니다")
}

// 라이브러리는 java, bom 은 javaPlatform 컴포넌트를 배포한다. 컴포넌트는 다른 플러그인이 나중에 등록하므로 등록될 때 연결한다.
components.matching { it.name == "java" || it.name == "javaPlatform" }
    .configureEach { publication.from(this) }
