package com.office.calendar.planner;

import com.office.calendar.planner.util.StartTimes;
import com.office.calendar.planner.util.UploadFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/planner")
@RequiredArgsConstructor
public class PlannerController {

    final private PlannerService plannerService;
    final private UploadFileService uploadFileService;

    @GetMapping({"", "/"})
    public String home() {
        log.info("home()");

        String nextPage = "planner/home";

        return nextPage;

    }

    // 일정 등록: 폼에서 받는 값은 화면에서 입력하는 항목(년·월·일·제목·내용)만 쓴다.
    // 번호·소유자·이미지 이름 같은 값은 클라이언트가 보내도 무시한다(번호를 보내 남의 일정을 덮어쓰는 것을 막음).
    @PostMapping("/plan")
    public ResponseEntity<Map<String, Object>> writePlan(
            PlannerDto form,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Principal principal) {
        log.info("writePlan()");

        String signInedMemberID = principal.getName();

        // 시작 시간은 선택이다. 값이 있으면 HH:mm 형식이어야 한다(파일을 저장하기 전에 먼저 확인)
        String startTime;
        try {
            startTime = StartTimes.normalize(form.getStart_time());
        } catch (IllegalArgumentException e) {
            return invalidStartTime();
        }

        PlannerDto plannerDto = PlannerDto.builder()
                .owner_id(signInedMemberID)
                .year(form.getYear())
                .month(form.getMonth())
                .date(form.getDate())
                .title(form.getTitle())
                .body(form.getBody())
                .start_time(startTime)
                .build();

        // 이미지는 선택이다. 파일을 골랐을 때만 저장하고, 안 골랐거나 빈 파일이면 이미지 없이 등록한다.
        if (hasFile(file)) {
            String savedFileName = uploadFileService.upload(signInedMemberID, file);
            if (savedFileName == null) {
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("message", "File upload failed!!");
                return ResponseEntity.badRequest().body(errorMap);      // 400 BAD

            }
            plannerDto.setImg_name(savedFileName);

        }

        Map<String, Object> resultMap = plannerService.writePlan(plannerDto);
        return ResponseEntity.ok(resultMap);    // 200 OK

    }

    // 시작 시간 형식 오류 응답(400)
    private ResponseEntity<Map<String, Object>> invalidStartTime() {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("code", "INVALID_START_TIME");
        errorMap.put("message", "시작 시간은 HH:mm 형식이어야 합니다.");
        return ResponseEntity.badRequest().body(errorMap);

    }

    // 파일을 실제로 골랐는지(없거나 빈 파일이면 false)
    private boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    // 일정들 가져오기
    @GetMapping("/plans")
    public ResponseEntity<Map<String, Object>> getPlans(
            @RequestParam("year") int year,
            @RequestParam("month") int month,
            Principal principal) {
        log.info("getPlans()");

        Map<String, Object> resultMap = plannerService.getPlans(year, month, principal.getName());

        return ResponseEntity.ok(resultMap);

    }

    // 일정 검색: 본인 일정만. /plan/{no} 같은 경로와 겹치지 않게 /plans/search로 둔다
    @GetMapping("/plans/search")
    public ResponseEntity<Map<String, Object>> searchPlans(
            @RequestParam(value = "keyword", required = false) String keyword,
            Principal principal) {
        log.info("searchPlans()");

        return ResponseEntity.ok(plannerService.searchPlans(keyword, principal.getName()));

    }

    // 일정 가져오기: 본인 일정이 아니면 없는 일정과 같은 404
    @GetMapping("/plan")
    public ResponseEntity<Map<String, Object>> getPlan(
            @RequestParam("no") int no,
            Principal principal
    ) {
        log.info("getPlan()");

        Map<String, Object> resultMap = plannerService.getPlan(no, principal.getName());
        if (resultMap.get("plan") == null) {
            return ResponseEntity.notFound().build();      // 404 NOT FOUND

        }

        return ResponseEntity.ok(resultMap);

    }

    // 일정 삭제하기: 본인 일정이 아니면 404
    @DeleteMapping("/plan/{no}")
    public ResponseEntity<Map<String, Object>> removePlan(
            @PathVariable("no") int no,
            Principal principal) {
        log.info("removePlan()");

        Map<String, Object> resultMap = plannerService.removePlan(no, principal.getName());
        if ((int) resultMap.get("result") <= plannerService.PLAN_REMOVE_FAIL) {
            return ResponseEntity.notFound().build();      // 404 NOT FOUND

        }

        return ResponseEntity.ok(resultMap);

    }

    // 일정 수정하기: 본인 일정이 아니면 404(파일 업로드 전에 먼저 확인).
    // 폼에서 받는 값은 일정 번호(경로)와 년·월·일·제목·내용만 쓰고, 이미지 이름은 서버가 저장한 것만 쓴다.
    @PutMapping("/plan/{no}")
    public ResponseEntity<Map<String, Object>> modifyPlan(
            @PathVariable("no") int no,
            PlannerDto form,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Principal principal
    ) {
        log.info("modifyPlan()");

        String signInedMemberID = principal.getName();
        if (!plannerService.isOwner(no, signInedMemberID)) {
            return ResponseEntity.notFound().build();      // 404 NOT FOUND

        }

        String startTime;
        try {
            startTime = StartTimes.normalize(form.getStart_time());
        } catch (IllegalArgumentException e) {
            return invalidStartTime();
        }

        PlannerDto plannerDto = PlannerDto.builder()
                .no(no)
                .year(form.getYear())
                .month(form.getMonth())
                .date(form.getDate())
                .title(form.getTitle())
                .body(form.getBody())
                .start_time(startTime)
                .build();

        if (hasFile(file)) {
            String savedFileName = uploadFileService.upload(signInedMemberID, file);
            if (savedFileName != null) {
                plannerDto.setImg_name(savedFileName);

            } else {
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("message", "File upload Fail");
                return ResponseEntity.badRequest().body(errorMap);

            }

        }

        Map<String, Object> resultMap = plannerService.modifyPlan(plannerDto, signInedMemberID);
        if ((int) resultMap.get("result") <= plannerService.PLAN_MODIFY_FAIL) {
            return ResponseEntity.notFound().build();      // 404 NOT FOUND

        }

        return ResponseEntity.ok(resultMap);

    }

}
