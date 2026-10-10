package com.dazo66.security;
import com.dazo66.util.ResultEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
@RestControllerAdvice
public class AclExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ResultEntity<Object>> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatus()).body(ResultEntity.failed(e.getReason()));
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ResultEntity<Object>> uploadTooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ResultEntity.failed("文件最大500MB"));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResultEntity<Object>> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ResultEntity.failed(e.getMessage()));
    }
}
