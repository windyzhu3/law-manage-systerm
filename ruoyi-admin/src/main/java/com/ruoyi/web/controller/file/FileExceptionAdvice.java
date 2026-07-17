package com.ruoyi.web.controller.file;

import com.law.file.domain.FileException;
import com.law.file.security.FileAccessDeniedException;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes=FileObjectController.class)
public class FileExceptionAdvice
{
    @ExceptionHandler(FileException.class)
    public AjaxResult handle(FileException error)
    {
        int status=error instanceof FileAccessDeniedException?HttpStatus.FORBIDDEN:HttpStatus.ERROR;
        return AjaxResult.error(status,error.getMessage()).put("businessCode",error.getBusinessCode());
    }
}
