import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/** Reproduce the directory lookup Spring performs before scanning extension YAML. */
class VerifyClasspath {
    public static void main(String[] args) throws Exception {
        try (var loader = new URLClassLoader(
                new URL[] {Path.of(args[0]).toUri().toURL()}, null)) {
            if (!loader.getResources("extensions/").hasMoreElements()) {
                throw new IllegalStateException("extensions/ is not discoverable on the classpath");
            }
            try (var setting = loader.getResourceAsStream("extensions/aoste-updates-setting.yaml")) {
                if (setting == null || !new String(setting.readAllBytes(), StandardCharsets.UTF_8)
                        .contains("name: commerce-settings")) {
                    throw new IllegalStateException("commerce-settings is not discoverable");
                }
            }
        }
        System.out.println("PASS: Java URLClassLoader discovers extensions/ and commerce-settings");
    }
}
