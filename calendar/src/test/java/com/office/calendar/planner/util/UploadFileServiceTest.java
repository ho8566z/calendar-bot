package com.office.calendar.planner.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

// 업로드 파일 종류 검사: 저장된 파일은 같은 사이트 주소로 제공되므로 이미지가 아닌 파일은 받지 않는다.
class UploadFileServiceTest {

    private static MockMultipartFile file(String name, String type) {
        return new MockMultipartFile("file", name, type, new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("jpg·png·gif·webp 이미지는 대소문자와 관계없이 허용한다")
    void imagesAllowed() {
        assertThat(UploadFileService.isAllowedImage(file("a.jpg", "image/jpeg"))).isTrue();
        assertThat(UploadFileService.isAllowedImage(file("a.JPEG", "image/jpeg"))).isTrue();
        assertThat(UploadFileService.isAllowedImage(file("a.png", "image/png"))).isTrue();
        assertThat(UploadFileService.isAllowedImage(file("a.gif", "image/gif"))).isTrue();
        assertThat(UploadFileService.isAllowedImage(file("a.WebP", "image/webp"))).isTrue();
    }

    @Test
    @DisplayName("html·svg·스크립트처럼 실행될 수 있는 파일은 이미지 종류를 내세워도 거부한다")
    void executableRejected() {
        assertThat(UploadFileService.isAllowedImage(file("x.html", "text/html"))).isFalse();
        assertThat(UploadFileService.isAllowedImage(file("x.html", "image/png"))).isFalse();
        assertThat(UploadFileService.isAllowedImage(file("x.svg", "image/svg+xml"))).isFalse();
        assertThat(UploadFileService.isAllowedImage(file("x.png", "image/svg+xml"))).isFalse();
        assertThat(UploadFileService.isAllowedImage(file("x.sh", "application/x-sh"))).isFalse();
        assertThat(UploadFileService.isAllowedImage(file("x.js", "text/javascript"))).isFalse();
    }

    @Test
    @DisplayName("확장자가 없거나 허용 확장자가 아니거나 종류가 이미지가 아니면 거부한다")
    void otherRejected() {
        assertThat(UploadFileService.isAllowedImage(file("noext", "image/png"))).isFalse();
        assertThat(UploadFileService.isAllowedImage(file("a.png", "text/plain"))).isFalse();
        assertThat(UploadFileService.isAllowedImage(file("a.png", null))).isFalse();
        assertThat(UploadFileService.isAllowedImage(file("a.png.html", "image/png"))).isFalse();
        assertThat(UploadFileService.isAllowedImage(null)).isFalse();
    }

    @Test
    @DisplayName("이미지가 아니면 저장하지 않고 null 을 돌려준다(컨트롤러가 400 으로 응답)")
    void uploadRejectsNonImage() {
        assertThat(new UploadFileService().upload("alice", file("x.html", "text/html"))).isNull();
    }

}
