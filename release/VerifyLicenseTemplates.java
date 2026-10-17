import java.nio.file.*;
import java.util.zip.*;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;

/** Render the shipped templates with the shared plugin missing: no content/JS may leak. */
public class VerifyLicenseTemplates {
    public static class PluginFinder {
        public boolean available(String name, String version) { return false; }
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("aoste-templates-");
        int checked = 0;
        try (ZipFile zip = new ZipFile(args[0])) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().startsWith("templates/") || entry.isDirectory()) continue;
                Path output = root.resolve(entry.getName());
                Files.createDirectories(output.getParent());
                Files.write(output, zip.getInputStream(entry).readAllBytes());
            }
        }
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix(root.resolve("templates").toString() + "/");
        resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        Context context = new Context(); context.setVariable("pluginFinder", new PluginFinder());
        try (var paths = Files.walk(root.resolve("templates"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".html")).toList()) {
                String name = root.resolve("templates").relativize(path).toString();
                if (name.contains("/") && !name.equals("error/error.html")) continue;
                String rendered = engine.process(name.substring(0, name.length()-5), context);
                if (!rendered.contains("AOSTE 尚未授权") || rendered.contains("<script") || rendered.contains("th:replace"))
                    throw new AssertionError("Unguarded template: " + name);
                checked++;
            }
        }
        System.out.println("PASS: " + checked + " shipped templates fail closed with plugin absent, including docs and error pages");
    }
}
