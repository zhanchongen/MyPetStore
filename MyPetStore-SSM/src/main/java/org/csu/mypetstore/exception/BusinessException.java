package org.csu.mypetstore.exception;

/** 可向用户展示的业务规则失败，HTTP 400。 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
