package local.halo.commerce;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/** Converts product copy into inert markup suitable for the theme description section. */
public final class ProductDescription {
    public String render(String content, String format) {
        if (content == null || content.isBlank()) return "";
        if (!"html".equals(format)) {
            var element = new org.jsoup.nodes.Element("p").text(content);
            element.attr("class", "whitespace-pre-wrap");
            return element.outerHtml();
        }
        var allowed = Safelist.relaxed().preserveRelativeLinks(true).addTags("hr", "del", "s", "span")
            .addAttributes("code", "class").addAttributes("a", "rel")
            .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");
        return Jsoup.clean(content, "https://commerce.invalid/", allowed,
            new Document.OutputSettings().prettyPrint(false));
    }
}
