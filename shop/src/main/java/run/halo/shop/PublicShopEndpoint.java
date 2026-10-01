package run.halo.shop;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

@Component
@RequiredArgsConstructor
public class PublicShopEndpoint implements CustomEndpoint {
    private final ShopService shop;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route()
            .GET("products", request -> shop.products().collectList()
                .flatMap(ServerResponse.ok()::bodyValue), builder -> builder.operationId("shopProducts"))
            .GET("products/{name}", request -> shop.product(request.pathVariable("name"))
                .flatMap(ServerResponse.ok()::bodyValue), builder -> builder.operationId("shopProduct"))
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.shop.cnmbdb.github.io/v1alpha1");
    }
}
