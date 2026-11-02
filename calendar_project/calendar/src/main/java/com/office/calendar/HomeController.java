package com.office.calendar;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
public class HomeController {

//    final private String CLASS_NAME = "[HomeController] ";

    @GetMapping({"", "/"})
    public String home() {
//        System.out.println(CLASS_NAME.concat("home()"));
        log.debug("DEBUG:: home()");
        log.info("INFO:: home()");
        log.warn("WARN:: home()");
        log.error("ERROR:: home()");

        String nextPage = "home";

        return nextPage;

    }

}
