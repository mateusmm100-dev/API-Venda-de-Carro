package br.unifor.locadora.security;

import br.unifor.locadora.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.Instant;

/** Respostas 401 e 403 geradas pela camada de segurança, no mesmo formato JSON do restante da API. */
public class JsonSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper;

    public JsonSecurityHandlers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
            throws IOException {
        escrever(req, res, HttpStatus.UNAUTHORIZED,
                "Autenticação necessária: requisição sem identidade válida do gateway");
    }

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex)
            throws IOException {
        escrever(req, res, HttpStatus.FORBIDDEN,
                "Acesso negado: seu perfil não tem permissão para este recurso");
    }

    private void escrever(HttpServletRequest req, HttpServletResponse res, HttpStatus status, String mensagem)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), mensagem, req.getRequestURI(), null));
    }
}
