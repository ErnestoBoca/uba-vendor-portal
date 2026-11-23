package com.ubagroup.vendorportal.config;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

import com.ubagroup.vendorportal.common.ApiException;
import com.ubagroup.vendorportal.common.CurrentUser;
import com.ubagroup.vendorportal.service.ApprovalService;

/**
 * Erros de regra de negocio: nos pedidos /api/** devolve JSON {"error": "..."} (mostrado pelo JavaScript);
 * nas paginas mostra a pagina error-page.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final CurrentUser currentUser;
    private final ApprovalService approvals;

    public GlobalExceptionHandler(CurrentUser currentUser, ApprovalService approvals) {
        this.currentUser = currentUser;
        this.approvals = approvals;
    }

    private static boolean isApi(HttpServletRequest req) {
        return req.getRequestURI().startsWith("/api/");
    }

    private static ResponseEntity<Map<String, String>> json(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    /** Pagina de erro: o interceptor do layout nao corre quando ha excepcao, por isso acrescenta-se aqui. */
    private ModelAndView page(int status, String message) {
        ModelAndView mv = new ModelAndView("error-page");
        mv.addObject("active", "");
        mv.addObject("me", currentUser.info());
        mv.addObject("myPending", approvals.countMine());
        mv.addObject("can", currentUser.permissions());
        mv.setStatus(HttpStatus.valueOf(status));
        mv.addObject("message", message);
        return mv;
    }

    @ExceptionHandler(ApiException.class)
    public Object handle(ApiException e, HttpServletRequest req) {
        return isApi(req) ? json(e.getStatus(), e.getMessage()) : page(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handle(DataIntegrityViolationException e, HttpServletRequest req) {
        log.warn("Violacao de integridade", e);
        String m = "Os dados violam uma regra da base de dados (valor duplicado ou invalido).";
        return isApi(req) ? json(409, m) : page(409, m);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handle(MaxUploadSizeExceededException e, HttpServletRequest req) {
        String m = "Ficheiro demasiado grande (maximo 20 MB).";
        return isApi(req) ? json(413, m) : page(413, m);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Object handle(HttpMessageNotReadableException e, HttpServletRequest req) throws Exception {
        if (!isApi(req)) throw e;
        return json(400, "Dados invalidos no pedido.");
    }

    @ExceptionHandler(Exception.class)
    public Object handleOther(Exception e, HttpServletRequest req) throws Exception {
        if (!isApi(req)) throw e;
        log.error("Erro inesperado", e);
        return json(500, "Erro interno: " + e.getClass().getSimpleName());
    }
}
