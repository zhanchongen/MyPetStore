package org.csu.mypetstore.admin.exception;

/** 数据已变化/版本冲突，HTTP 409。 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
