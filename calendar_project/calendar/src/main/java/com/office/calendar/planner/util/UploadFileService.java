package com.office.calendar.planner.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.UUID;

@Slf4j
@Service
public class UploadFileService {

    public String upload(String id, MultipartFile file) {
        log.info("upload()");

        boolean result = false;

        if (file == null || file.isEmpty()) {
            log.info("FILE UPLOAD FAIL!! (EMPTY FILE)");
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
            e.printStackTrace();

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
