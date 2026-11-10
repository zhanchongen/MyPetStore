package org.csu.mypetstore.exception;

/** 权限/归属/表单防护失败，HTTP 403。 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
