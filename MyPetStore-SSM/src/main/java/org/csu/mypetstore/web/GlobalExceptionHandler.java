package org.csu.mypetstore.web;

import jakarta.servlet.http.HttpServletResponse;
import org.csu.mypetstore.exception.BadRequestException;
import org.csu.mypetstore.exception.BusinessException;
import org.csu.mypetstore.exception.ConflictException;
import org.csu.mypetstore.exception.ForbiddenException;
import org.csu.mypetstore.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({BadRequestException.class, BusinessException.class})
    public ModelAndView badRequest(RuntimeException ex, HttpServletResponse response) {
        return error("error/400", 400, ex.getMessage(), response);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ModelAndView forbidden(ForbiddenException ex, HttpServletResponse response) {
        return error("error/403", 403, ex.getMessage(), response);
    }

    @ExceptionHandler(NotFoundException.class)
    public ModelAndView notFound(NotFoundException ex, HttpServletResponse response) {
        return error("error/404", 404, ex.getMessage(), response);
    }

    @ExceptionHandler(ConflictException.class)
    public ModelAndView conflict(ConflictException ex, HttpServletResponse response) {
        return error("error/409", 409, ex.getMessage(), response);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ModelAndView missingParameter(Exception ex, HttpServletResponse response) {
        return error("error/400", 400, "请求参数不正确，请检查后重试。", response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView noResource(NoResourceFoundException ex, HttpServletResponse response) {
        return error("error/404", 404, "页面不存在。", response);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ModelAndView duplicateKey(DuplicateKeyException ex, HttpServletResponse response) {
        log.warn("唯一约束冲突: {}", ex.getMostSpecificCause().getMessage());
        return error("error/409", 409, "数据已存在或发生冲突，请刷新后重试。", response);
    }

    @ExceptionHandler(DataAccessException.class)
    public ModelAndView dataAccess(DataAccessException ex, HttpServletResponse response) {
        log.error("数据库访问失败", ex);
        return error("error/500", 500, "数据处理失败，请稍后重试。", response);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unknown(Exception ex, HttpServletResponse response) {
        log.error("未处理异常", ex);
        return error("error/500", 500, "系统处理失败，请稍后重试。", response);
    }

    private ModelAndView error(String view, int status, String message, HttpServletResponse response) {
        response.setStatus(status);
        ModelAndView mav = new ModelAndView(view);
        mav.addObject("message", message == null || message.isBlank() ? "请求处理失败。" : message);
        mav.addObject("status", status);
        return mav;
    }
}
