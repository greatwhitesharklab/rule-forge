package com.ruleforge;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Splash {
    public void print() {
        String sb = "\n" +
                "_____  __________ _____  ________ ________________ " +
                "\n" +
                "__  / / /___  __ \\__  / / /___  / ___  ____/__|__ \\" +
                "\n" +
                "_  / / / __  /_/ /_  / / / __  /  __  __/   ____/ /" +
                "\n" +
                "/ /_/ /  _  _, _/ / /_/ /  _  /____  /___   _  __/ " +
                "\n" +
                "\\____/   /_/ |_|  \\____/   /_____//_____/   /____/ " +
                "\n" +
                "....................................................................................................." +
                "\n" +
                ".  uRule, is a Chinese style rule engine" +
                " licensed under the Apache License 2.0,                     ." +
                "\n" +
                ".  which is open-source, easy to use," +
                "high-performance, with browser-based-designer.                  ." +
                "\n" +
                "....................................................................................................." +
                "\n";
        log.info(sb);
    }
}
