package com.office.calendar.planner;

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

    @PostMapping("/plan")
    public ResponseEntity<Map<String, Object>> writePlan(
            PlannerDto plannerDto,
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        log.info("writePlan()");

        String signInedMemberID = principal.getName();

        // SAVE FILE
        String savedFileName = uploadFileService.upload(signInedMemberID, file);
        if (savedFileName != null) {
            plannerDto.setImg_name(savedFileName);
            plannerDto.setOwner_id(signInedMemberID);

            Map<String, Object> resultMap = plannerService.writePlan(plannerDto);
            return ResponseEntity.ok(resultMap);    // 200 OK

        } else {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("message", "File upload failed!!");
            return ResponseEntity.badRequest().body(errorMap);      // 400 BAD

        }

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

    // 일정 수정하기: 본인 일정이 아니면 404(파일 업로드 전에 먼저 확인)
    @PutMapping("/plan/{no}")
    public ResponseEntity<Map<String, Object>> modifyPlan(
            @PathVariable("no") int no,
            PlannerDto plannerDto,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Principal principal
    ) {
        log.info("modifyPlan()");

        String signInedMemberID = principal.getName();
        if (!plannerService.isOwner(no, signInedMemberID)) {
            return ResponseEntity.notFound().build();      // 404 NOT FOUND

        }

        plannerDto.setNo(no);
        if (file != null) {
            String savedFileName = uploadFileService.upload(signInedMemberID, file);
            if (savedFileName != null) {
                plannerDto.setImg_name(savedFileName);
                plannerDto.setOri_owner_id(signInedMemberID);

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
