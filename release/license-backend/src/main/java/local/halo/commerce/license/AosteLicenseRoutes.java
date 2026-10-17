package local.halo.commerce.license;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Configuration
public class AosteLicenseRoutes {
    public record Input(String key) {}

    @Bean
    RouterFunction<ServerResponse> aosteLicenseRoutes(AosteLicenseService licenses) {
        return RouterFunctions.route()
            .GET("/shop/api/admin/license", r -> administrator().then(licenses.status()).flatMap(this::response))
            .PUT("/shop/api/admin/license", r -> administrator().then(r.bodyToMono(Input.class))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST)))
                .flatMap(input -> licenses.save(input.key())).flatMap(status ->
                    ServerResponse.status(status.valid() ? 200 : status.state().equals("unavailable") ? 503 : 403)
                        .header("Cache-Control", "no-store").bodyValue(status)))
            .DELETE("/shop/api/admin/license", r -> administrator().then(licenses.clear()).flatMap(this::response))
            .build().filter((request, next) -> next.handle(request)
                .onErrorResume(ResponseStatusException.class, error -> ServerResponse.status(error.getStatusCode())
                    .header("Cache-Control", "no-store").bodyValue(Map.of("message", "授权管理需要超级管理员权限，或请求格式不正确。"))));
    }

    private Mono<Void> administrator() {
        return ReactiveSecurityContextHolder.getContext().map(c -> c.getAuthentication())
            .filter(a -> a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED)))
            .filter(a -> a.getAuthorities().stream().anyMatch(role -> "ROLE_super-role".equals(role.getAuthority())))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN))).then();
    }

    private Mono<ServerResponse> response(AosteLicenseService.Status status) {
        return ServerResponse.ok().header("Cache-Control", "no-store").bodyValue(status);
    }
}
