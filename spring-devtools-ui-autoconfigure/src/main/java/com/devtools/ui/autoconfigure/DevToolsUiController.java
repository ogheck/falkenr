package com.devtools.ui.autoconfigure;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class DevToolsUiController {

    @GetMapping({DevToolsUiConstants.ROOT_PATH, DevToolsUiConstants.ROOT_PATH + "/"})
    String index() {
        return "forward:" + DevToolsUiConstants.INDEX_PATH;
    }
}
