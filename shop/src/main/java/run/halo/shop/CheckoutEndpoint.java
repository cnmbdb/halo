package run.halo.shop;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

@Component
@RequiredArgsConstructor
public class CheckoutEndpoint implements CustomEndpoint {
    private final ShopService shop;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route()
            .POST("orders", request -> request.bodyToMono(ShopService.OrderRequest.class)
                .flatMap(shop::placeOrder)
                .flatMap(receipt -> ServerResponse.status(HttpStatus.CREATED).bodyValue(receipt)),
                builder -> builder.operationId("createShopOrder"))
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("uc.api.shop.cnmbdb.github.io/v1alpha1");
    }
}
