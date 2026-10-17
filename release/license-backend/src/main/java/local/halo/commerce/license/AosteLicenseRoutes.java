package local.halo.commerce.license;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Configuration
public class AosteLicenseRoutes {
    public record Input(String key) {}
    private record LicenseResponse(boolean valid, String state, String message, String expiresAt, String csrfToken) {
        static LicenseResponse from(AosteLicenseService.Status status, String csrfToken) {
            return new LicenseResponse(status.valid(), status.state(), status.message(), status.expiresAt(), csrfToken);
        }
    }

    @Bean
    RouterFunction<ServerResponse> aosteLicenseRoutes(AosteLicenseService licenses) {
        return RouterFunctions.route()
            .GET("/shop/api/admin/license", r -> administrator().then(licenses.status()).flatMap(status -> response(r, status)))
            .PUT("/shop/api/admin/license", r -> administrator().then(r.bodyToMono(Input.class))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST)))
                .flatMap(input -> licenses.save(input.key())).flatMap(status ->
                    ServerResponse.status(status.valid() ? 200 : status.state().equals("unavailable") ? 503 : 403)
                        .header("Cache-Control", "no-store").bodyValue(status)))
            .DELETE("/shop/api/admin/license", r -> administrator().then(licenses.clear()).flatMap(status -> response(r, status)))
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

    private Mono<ServerResponse> response(org.springframework.web.reactive.function.server.ServerRequest request,
                                          AosteLicenseService.Status status) {
        return csrfToken(request.exchange()).flatMap(token -> ServerResponse.ok()
            .header("Cache-Control", "no-store").bodyValue(LicenseResponse.from(status, token)));
    }

    @SuppressWarnings("unchecked")
    Mono<String> csrfToken(org.springframework.web.server.ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(CsrfToken.class.getName());
        if (attribute instanceof Mono<?> token) {
            return ((Mono<CsrfToken>) token).map(CsrfToken::getToken);
        }
        if (attribute instanceof CsrfToken token) return Mono.just(token.getToken());
        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN));
    }
}
