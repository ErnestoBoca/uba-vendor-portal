package com.ubagroup.vendorportal.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.ubagroup.vendorportal.common.CurrentUser;
import com.ubagroup.vendorportal.service.ApprovalService;

/** Acrescenta ao modelo das paginas os dados do layout (utilizador no topo e contador de aprovacoes no menu). */
@Component
public class LayoutInterceptor implements HandlerInterceptor {

    private final CurrentUser currentUser;
    private final ApprovalService approvals;

    public LayoutInterceptor(CurrentUser currentUser, ApprovalService approvals) {
        this.currentUser = currentUser;
        this.approvals = approvals;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView mv) {
        if (mv == null || mv.getViewName() == null || mv.getViewName().startsWith("redirect:")) {
            return;
        }
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a instanceof AnonymousAuthenticationToken || !a.isAuthenticated()) {
            return;
        }
        mv.addObject("me", currentUser.info());
        mv.addObject("myPending", approvals.countMine());
        mv.addObject("can", currentUser.permissions());
    }
}
