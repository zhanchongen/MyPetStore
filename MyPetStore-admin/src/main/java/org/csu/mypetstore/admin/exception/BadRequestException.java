package org.csu.mypetstore.admin.exception;

/** 参数/格式/业务校验失败，HTTP 400。 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
