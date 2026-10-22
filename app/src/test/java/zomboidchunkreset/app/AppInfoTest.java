package zomboidchunkreset.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppInfoTest {

    @Test
    void versionAndRepositoryComeFromTheBuild() {
        assertTrue(AppInfo.version().matches("\\d+\\.\\d+\\.\\d+"), AppInfo.version());
        assertTrue(AppInfo.repositoryUrl().startsWith("https://github.com/"), AppInfo.repositoryUrl());
    }
}
