package local.halo.commerce;

import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

final class BuyerIdentity {
    private static final String ATTRIBUTE = "commerce.guest-buyer";
    static Mono<String> resolve(ServerRequest request, boolean createGuest) {
        return ReactiveSecurityContextHolder.getContext()
            .map(context -> context.getAuthentication())
            .filter(auth -> auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken))
            .map(auth -> auth.getName())
            .switchIfEmpty(Mono.defer(() -> request.session().flatMap(session -> {
                String buyer = session.getAttribute(ATTRIBUTE);
                if (buyer == null && createGuest) {
                    buyer = "guest:" + UUID.randomUUID();
                    session.getAttributes().put(ATTRIBUTE, buyer);
                }
                return buyer == null ? Mono.error(new LoginRequired()) : Mono.just(buyer);
            })));
    }
    static boolean guest(String buyer) { return buyer.startsWith("guest:"); }
    static boolean allowed(Product product) {
        var annotations = product.getMetadata().getAnnotations();
        return annotations != null && "true".equals(annotations.get("commerce.halo.run/allow-guest-purchase"));
    }
    static final class LoginRequired extends RuntimeException {}
}
