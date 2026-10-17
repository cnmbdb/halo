package local.halo.commerce.license;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.DefaultCsrfToken;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AosteLicenseTest {
    final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    final String key = "ets_ak_" + "A".repeat(43);

    AosteLicenseService service(String body, HttpStatus status) {
        WebClient http = WebClient.builder().exchangeFunction(request -> {
            assertEquals(AosteLicenseService.VERIFY_URL, request.url().toString());
            assertEquals(HttpMethod.POST, request.method());
            return Mono.just(ClientResponse.create(status).header("Content-Type", "application/json").body(body).build());
        }).build();
        return new AosteLicenseService(client, http);
    }

    Secret stored() {
        Secret secret = new Secret();
        secret.setData(Map.of("key", key.getBytes(StandardCharsets.UTF_8)));
        when(client.fetch(Secret.class, AosteLicenseService.SECRET)).thenReturn(Mono.just(secret));
        return secret;
    }

    @Test void absentKeyDeniedWithoutNetwork() {
        when(client.fetch(Secret.class, AosteLicenseService.SECRET)).thenReturn(Mono.empty());
        assertEquals("missing", service("{}", HttpStatus.OK).status().block().state());
    }
    @Test void activeKeyAuthorizesBothComponents() {
        stored();
        assertTrue(service("{\"valid\":true,\"product\":\"aoste\",\"expiresAt\":null}", HttpStatus.OK).status().block().valid());
    }
    @Test void wrongProductFailsClosed() {
        assertFalse(service("{\"valid\":true,\"product\":\"another\"}", HttpStatus.OK).verify(key).block().valid());
    }
    @Test void expiredGrantFailsClosed() {
        assertFalse(service("{\"valid\":true,\"product\":\"aoste\",\"expiresAt\":\"2020-01-01T00:00:00Z\"}", HttpStatus.OK).verify(key).block().valid());
    }
    @Test void malformedExpiryFailsClosed() {
        assertFalse(service("{\"valid\":true,\"product\":\"aoste\",\"expiresAt\":\"bad\"}", HttpStatus.OK).verify(key).block().valid());
    }
    @Test void revokedKeyFailsClosed() {
        assertEquals("invalid", service("{}", HttpStatus.UNAUTHORIZED).verify(key).block().state());
    }
    @Test void serviceFailureNeverAuthorizes() {
        assertEquals("unavailable", service("{}", HttpStatus.SERVICE_UNAVAILABLE).verify(key).block().state());
    }
    @Test void invalidReplacementDoesNotSave() {
        assertFalse(service("{}", HttpStatus.FORBIDDEN).save(key).block().valid());
        verifyNoInteractions(client);
    }
    @Test void keyIsPersistedAsSecretNotStatusFlag() {
        when(client.fetch(Secret.class, AosteLicenseService.SECRET)).thenReturn(Mono.empty());
        when(client.create(any(Secret.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        assertTrue(service("{\"valid\":true,\"product\":\"aoste\"}", HttpStatus.OK).save(key).block().valid());
        verify(client).create(argThat((Secret secret) -> secret.getMetadata().getName().equals(AosteLicenseService.SECRET)
            && secret.getData() != null && key.equals(new String(secret.getData().get("key"), StandardCharsets.UTF_8))));
    }
    @Test void clearingInvalidatesActiveDecision() {
        Secret secret = stored();
        when(client.update(secret)).thenReturn(Mono.just(secret));
        AosteLicenseService service = service("{\"valid\":true,\"product\":\"aoste\"}", HttpStatus.OK);
        assertTrue(service.status().block().valid());
        service.clear().block();
        assertFalse(service.status().block().valid());
    }
    @Test void cacheDoesNotExtendGrantExpiry() throws Exception {
        stored();
        AosteLicenseService service = service("{\"valid\":true,\"product\":\"aoste\",\"expiresAt\":\"" + Instant.now().plusMillis(300) + "\"}", HttpStatus.OK);
        assertTrue(service.status().block().valid());
        Thread.sleep(350);
        assertFalse(service.status().block().valid());
    }
    @Test void gateBlocksThemeAndCheckoutWithNoJavascript() {
        AosteLicenseService licenses = mock(AosteLicenseService.class);
        when(licenses.status()).thenReturn(Mono.just(AosteLicenseService.missing()));
        ConfigMap config = new ConfigMap(); config.setData(Map.of("theme", "{\"active\":\"theme-aoset\"}"));
        when(client.fetch(ConfigMap.class, "system")).thenReturn(Mono.just(config));
        when(client.fetch(ConfigMap.class, "system-default")).thenReturn(Mono.empty());
        AosteLicenseFilter filter = new AosteLicenseFilter(licenses, client);
        for (String path : new String[]{"/", "/archives/hello", "/shop", "/shop/api/products", "/shop/api/orders", "/apis/commerce.halo.run/v1alpha1/products"}) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path));
            filter.filter(exchange, e -> { fail("Gate bypass: " + path); return Mono.empty(); }).block();
            assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        }
    }
    @Test void recoveryAndExistingTransactionsStayAvailable() {
        AosteLicenseService licenses = mock(AosteLicenseService.class);
        AosteLicenseFilter filter = new AosteLicenseFilter(licenses, client);
        for (String path : new String[]{"/console/aoste/updates", "/shop/api/admin/license", "/shop/payment/notify", "/shop/api/orders/test/digital-delivery", "/shop/api/admin/orders", "/shop/api/admin/updates/releases/v/file"}) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
            boolean[] called = {false};
            filter.filter(exchange, e -> { called[0] = true; return Mono.empty(); }).block();
            assertTrue(called[0], path);
        }
        verifyNoInteractions(licenses);
    }
    @Test void otherThemesAreUnaffected() {
        ConfigMap config = new ConfigMap(); config.setData(Map.of("theme", "{\"active\":\"theme-earth\"}"));
        when(client.fetch(ConfigMap.class, "system")).thenReturn(Mono.just(config));
        when(client.fetch(ConfigMap.class, "system-default")).thenReturn(Mono.empty());
        AosteLicenseService licenses = mock(AosteLicenseService.class);
        boolean[] called = {false};
        new AosteLicenseFilter(licenses, client).filter(MockServerWebExchange.from(MockServerHttpRequest.get("/")), e -> { called[0] = true; return Mono.empty(); }).block();
        assertTrue(called[0]); verifyNoInteractions(licenses);
    }
    @Test void licenseRoutesRequireSuperAdministrator() {
        AosteLicenseService licenses = mock(AosteLicenseService.class);
        when(licenses.status()).thenReturn(Mono.just(AosteLicenseService.missing()));
        var routes = new AosteLicenseRoutes().aosteLicenseRoutes(licenses);
        for (String role : new String[]{"ROLE_anonymous", "ROLE_editor", "ROLE_super-role"}) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/shop/api/admin/license"));
            exchange.getAttributes().put(CsrfToken.class.getName(), Mono.just(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "session-bound-token")));
            var request = ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders());
            var auth = new UsernamePasswordAuthenticationToken("admin", "unused", java.util.List.of(new SimpleGrantedAuthority(role)));
            var response = routes.route(request).flatMap(handler -> handler.handle(request))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)).block();
            assertEquals(role.equals("ROLE_super-role") ? 200 : 403, response.statusCode().value());
        }
    }
    @Test void csrfTokenIsReadFromHaloSecurityContext() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/shop/api/admin/license"));
        exchange.getAttributes().put(CsrfToken.class.getName(), Mono.just(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "session-bound-token")));
        assertEquals("session-bound-token", new AosteLicenseRoutes().csrfToken(exchange).block());
    }
}
