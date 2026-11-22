// 루트 프로젝트용: 두 샘플의 공통 소스가 같은지 check 에서 확인하는 checkSampleSync 태스크를 등록한다.
// 비교할 디렉터리와 허용하는 차이는 루트 build.gradle.kts 가 정한다.
plugins {
    base
}

val checkSampleSync = tasks.register<SampleSourcesSyncCheck>("checkSampleSync") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "두 샘플(src)의 공통 파일이 같은지 확인한다"
    result.set(layout.buildDirectory.file("sample-sync/result.txt"))
}

tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
    dependsOn(checkSampleSync)
}
