package com.ubagroup.vendorportal.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.ubagroup.vendorportal.common.ApiException;
import com.ubagroup.vendorportal.common.CurrentUser;
import com.ubagroup.vendorportal.entity.AppUser;

/**
 * Controlo de acesso por pedido (lido da BD, por isso alteracoes de perfil/estado tem efeito imediato):
 *  - utilizador desactivado: a sessao termina;
 *  - Settings (paginas e APIs de configuracao): so SYS_ADMIN.
 * As restantes regras (quem regista vendors, quem aprova, ...) estao nos servicos.
 */
@Component
public class AccessInterceptor implements HandlerInterceptor {

    private static final String[] ADMIN_PREFIXES = {
        "/settings", "/api/settings", "/api/users", "/api/flows", "/api/service-areas", "/api/departments"
    };

    private final CurrentUser currentUser;

    public AccessInterceptor(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a instanceof AnonymousAuthenticationToken || !a.isAuthenticated()) {
            return true;
        }
        AppUser u = currentUser.user();
        if (!u.isActive()) {
            HttpSession s = request.getSession(false);
            if (s != null) s.invalidate();
            SecurityContextHolder.clearContext();
            if (request.getRequestURI().startsWith("/api/")) {
                response.sendError(401, "Utilizador desactivado.");
            } else {
                response.sendRedirect(request.getContextPath() + "/login?disabled");
            }
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (String p : ADMIN_PREFIXES) {
            if (path.equals(p) || path.startsWith(p + "/")) {
                if (!currentUser.isAdmin()) {
                    throw new ApiException("Apenas o administrador do sistema pode aceder as configuracoes.", 403);
                }
                break;
            }
        }
        return true;
    }
}
