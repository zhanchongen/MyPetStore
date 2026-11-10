package org.csu.mypetstore.admin.exception;

/** 对象不存在或当前用户不可见，HTTP 404。 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
