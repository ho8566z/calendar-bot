package com.office.calendar.planner.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class UploadFileService {

    // 올릴 수 있는 이미지 확장자. 저장한 파일은 같은 사이트 주소(/planUploadImg/**)로 그대로 제공되므로
    // html·svg·스크립트처럼 브라우저에서 실행될 수 있는 파일은 받지 않는다.
    static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp");

    // 이미지 파일인지 확인한다: 확장자가 허용 목록에 있고, 브라우저가 알려 준 종류가 image/* 이며 svg가 아니어야 한다
    public static boolean isAllowedImage(MultipartFile file) {
        if (file == null) {
            return false;
        }
        String name = file.getOriginalFilename();
        int dot = name != null ? name.lastIndexOf(".") : -1;
        if (dot < 0 || !ALLOWED_EXTENSIONS.contains(name.substring(dot).toLowerCase(Locale.ROOT))) {
            return false;
        }
        String type = file.getContentType();
        return type != null && type.toLowerCase(Locale.ROOT).startsWith("image/") && !type.toLowerCase(Locale.ROOT).contains("svg");
    }

    public String upload(String id, MultipartFile file) {
        log.info("upload()");

        boolean result = false;

        if (file == null || file.isEmpty()) {
            log.info("FILE UPLOAD FAIL!! (EMPTY FILE)");
            return null;
        }

        if (!isAllowedImage(file)) {
            log.info("FILE UPLOAD FAIL!! (NOT AN IMAGE)");
            return null;
        }

        String fileOriName = file.getOriginalFilename(); // abc.jpg
        int dotIndex = fileOriName != null ? fileOriName.lastIndexOf(".") : -1;
        String fileExtension = dotIndex >= 0 ? fileOriName.substring(dotIndex) : ""; // .jpg (확장자 없으면 "")
        String uploadDir = "c:\\calendar\\upload\\" + id;

        UUID uuid = UUID.randomUUID();      // afawer-lui34q-23asdf
        String uniqueFileName = uuid.toString().replaceAll("-", "");    // afawerlui34q23asdf

        File saveFile = new File(uploadDir + "\\" + uniqueFileName + fileExtension); //afawerlui34q23asdf.jpg
        File saveDir = saveFile.getParentFile();
        if (!saveDir.exists())
            saveDir.mkdirs();

        try {

            file.transferTo(saveFile);
            result = true;

        } catch (Exception e) {
            log.error("upload 실패", e);

        }

        if (result) {
            log.info("FILE UPLOAD SUCCESS!!");
            return uniqueFileName + fileExtension;

        } else {
            log.info("FILE UPLOAD FAIL!!");
            return null;

        }

    }

}
