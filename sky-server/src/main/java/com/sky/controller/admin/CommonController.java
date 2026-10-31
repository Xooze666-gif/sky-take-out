package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 通用接口：图片上传
 * <p>
 * 生产环境通常走 OSS（阿里云/腾讯云），这里用本地磁盘演示，
 * 返回相对路径 /images/xxx，由静态资源映射对外暴露。
 */
@RestController
@RequestMapping("/admin/common")
public class CommonController {

    @Value("${sky.file.upload-dir}")
    private String uploadDir;

    /**
     * 上传图片
     * POST /admin/common/upload  (multipart/form-data, 字段名 file)
     */
    @PostMapping("/upload")
    public Result<String> upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("上传文件为空");
        }

        String originalName = file.getOriginalFilename();
        // 取扩展名，原文件名不信任（可能含路径穿越字符）
        String ext = "";
        if (originalName != null && originalName.lastIndexOf('.') >= 0) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }

        // UUID 重命名，避免同名覆盖 + 路径穿越
        String newName = UUID.randomUUID().toString().replace("-", "") + ext;

        File dir = new File(uploadDir);
        if (!dir.exists() && !dir.mkdirs()) {
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }

        try {
            file.transferTo(new File(dir, newName).getAbsoluteFile());
        } catch (IOException e) {
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }

        return Result.success("/images/" + newName);
    }
}
