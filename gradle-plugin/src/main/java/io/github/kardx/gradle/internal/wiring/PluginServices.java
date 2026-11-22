package io.github.kardx.gradle.internal.wiring;

import javax.inject.Inject;
import org.gradle.api.file.FileSystemOperations;

/** 태스크 액션에서 쓰는 Gradle 서비스. {@link org.gradle.api.Project} 대신 주입받아 configuration cache 와 호환되게 한다. */
public abstract class PluginServices {
    @Inject
    public abstract FileSystemOperations getFs();
}
