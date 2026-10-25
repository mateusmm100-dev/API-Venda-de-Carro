package br.unifor.locadora.security;

import br.unifor.locadora.domain.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Confia na identidade informada pelo gateway B2C, mas só se a requisição provar que veio dele.
 *
 * Fluxo:
 *  1. O gateway valida o login do cliente no provedor B2C.
 *  2. O gateway remove quaisquer headers X-User-* vindos do cliente e injeta os seus próprios,
 *     junto com o segredo compartilhado (X-Gateway-Secret).
 *  3. Este filtro confere o segredo (comparação em tempo constante). Se estiver correto e a
 *     identidade for válida, autentica a requisição; caso contrário a requisição segue sem
 *     autenticação e o Spring Security responde 401.
 *
 * Assim, chamar o backend diretamente (sem passar pelo gateway) não permite forjar um perfil.
 */
public class GatewayAuthFilter extends OncePerRequestFilter {

    public static final String H_SECRET = "X-Gateway-Secret";
    public static final String H_USER_ID = "X-User-Id";
    public static final String H_USER_EMAIL = "X-User-Email";
    public static final String H_USER_NAME = "X-User-Name";
    public static final String H_USER_ROLE = "X-User-Role";

    private final byte[] segredo;

    public GatewayAuthFilter(GatewayProperties props) {
        if (props.sharedSecret() == null || props.sharedSecret().isBlank()) {
            throw new IllegalStateException("gateway.shared-secret (GATEWAY_SHARED_SECRET) não pode ser vazio");
        }
        this.segredo = props.sharedSecret().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String enviado = request.getHeader(H_SECRET);
        String id = limpar(request.getHeader(H_USER_ID));

        if (enviado != null && id != null
                && MessageDigest.isEqual(segredo, enviado.getBytes(StandardCharsets.UTF_8))) {
            Role role = lerRole(request.getHeader(H_USER_ROLE));
            if (role != null) {
                AuthenticatedUser usuario = new AuthenticatedUser(
                        id,
                        limpar(request.getHeader(H_USER_EMAIL)),
                        limpar(request.getHeader(H_USER_NAME)),
                        role);
                var autenticacao = new UsernamePasswordAuthenticationToken(
                        usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
                SecurityContextHolder.getContext().setAuthentication(autenticacao);
            }
        }
        chain.doFilter(request, response);
    }

    private static String limpar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.strip();
    }

    private static Role lerRole(String valor) {
        String limpo = limpar(valor);
        if (limpo == null) {
            return null;
        }
        try {
            return Role.valueOf(limpo.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
