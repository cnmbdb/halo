package run.halo.shop;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import run.halo.app.theme.TemplateNameResolver;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@Component
public class ShopPageRouter {
    private final TemplateNameResolver templates;

    public ShopPageRouter(TemplateNameResolver templates) {
        this.templates = templates;
    }

    @Bean
    RouterFunction<ServerResponse> shopPage() {
        return RouterFunctions.route(GET("/shop"), request -> templates
            .resolveTemplateNameOrDefault(request.exchange(), "shop")
            .flatMap(name -> ServerResponse.ok().render(name, Map.of("_templateId", "shop"))));
    }
}
